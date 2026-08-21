import api from './api.js';
import { updateTicketNavigationCount } from './navigation.js';

const STATUS_LABELS = Object.freeze({
    RECEBIDO: 'Recebido',
    EM_TRIAGEM: 'Em triagem',
    EM_ATENDIMENTO: 'Em atendimento',
    AGUARDANDO_CLIENTE: 'Aguardando cliente',
    AGUARDANDO_PECA: 'Aguardando peça',
    RESOLVIDO: 'Resolvido',
    FECHADO: 'Fechado'
});

const PRIORITY_LABELS = Object.freeze({
    BAIXA: 'Baixa',
    NORMAL: 'Normal',
    ALTA: 'Alta',
    CRITICA: 'Crítica'
});

const TYPE_LABELS = Object.freeze({
    GERAL: 'Geral',
    HARDWARE: 'Hardware',
    SOFTWARE: 'Software'
});

const CLOSED_STATUSES = new Set(['RESOLVIDO', 'FECHADO']);
const UNASSIGNED_FILTER = '__UNASSIGNED__';

function getTicketCode(ticket) {
    const compactId = String(ticket.id || '').replaceAll('-', '').slice(0, 6).toUpperCase();
    return `SPD-${compactId || '000000'}`;
}

function parseDate(value) {
    if (!value) return null;
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
}

function formatDate(value) {
    const date = parseDate(value);
    if (!date) return 'Data indisponível';
    return new Intl.DateTimeFormat('pt-BR', {
        dateStyle: 'short',
        timeStyle: 'short'
    }).format(date);
}

function formatDuration(milliseconds) {
    const totalMinutes = Math.max(1, Math.ceil(Math.abs(milliseconds) / 60000));
    if (totalMinutes < 60) return `${totalMinutes}min`;

    const totalHours = Math.floor(totalMinutes / 60);
    if (totalHours < 24) {
        const minutes = totalMinutes % 60;
        return minutes > 0 ? `${totalHours}h ${minutes}min` : `${totalHours}h`;
    }

    const days = Math.floor(totalHours / 24);
    const hours = totalHours % 24;
    return hours > 0 ? `${days}d ${hours}h` : `${days}d`;
}

function getSlaInfo(ticket) {
    const deadline = parseDate(ticket.dataVencimento);
    if (!deadline) return { label: 'Prazo não informado', tone: '' };
    if (CLOSED_STATUSES.has(ticket.status)) return { label: 'Finalizado', tone: 'is-complete' };

    const difference = deadline.getTime() - Date.now();
    if (difference <= 0) {
        return { label: `Vencido há ${formatDuration(difference)}`, tone: 'is-overdue' };
    }
    if (difference <= 24 * 60 * 60 * 1000) {
        return { label: `${formatDuration(difference)} restantes`, tone: 'is-risk' };
    }
    return { label: `${formatDuration(difference)} restantes`, tone: '' };
}

function statusClass(status) {
    if (status === 'RECEBIDO') return 'status-recebido';
    if (status === 'EM_TRIAGEM') return 'status-triagem';
    if (status === 'EM_ATENDIMENTO') return 'status-atendimento';
    if (status === 'RESOLVIDO') return 'status-resolvido';
    if (status === 'FECHADO') return 'status-fechado';
    return 'status-aguardando';
}

function priorityClass(priority) {
    return {
        BAIXA: 'p-baixa',
        NORMAL: 'p-normal',
        ALTA: 'p-alta',
        CRITICA: 'p-critica'
    }[priority] || 'p-baixa';
}

function createBadge(label, className) {
    const badge = document.createElement('span');
    badge.className = `badge ${className}`;
    badge.textContent = label;
    return badge;
}

document.addEventListener('DOMContentLoaded', () => {
    const session = api.requireAuth();
    if (!session) return;

    const role = String(session.role || '').toUpperCase();
    const elements = {
        pageTitle: document.getElementById('ticketsPageTitle'),
        pageDescription: document.getElementById('ticketsPageDescription'),
        total: document.getElementById('ticketsTotal'),
        summary: document.getElementById('resultsSummary'),
        form: document.getElementById('ticketFilters'),
        query: document.getElementById('ticketQuery'),
        status: document.getElementById('statusFilter'),
        priority: document.getElementById('priorityFilter'),
        type: document.getElementById('typeFilter'),
        category: document.getElementById('categoryFilter'),
        technician: document.getElementById('technicianFilter'),
        clear: document.getElementById('clearFiltersBtn'),
        body: document.getElementById('ticketsTableBody')
    };

    const state = {
        categories: [],
        technicians: new Map(),
        requestController: null,
        requestSequence: 0,
        searchTimer: null,
        initialCategoryId: '',
        initialTechnicianId: ''
    };

    const pageCopy = {
        CLIENTE: {
            title: 'Meus Chamados',
            description: 'Acompanhe todas as solicitações abertas por você e seus respectivos prazos.'
        },
        TECNICO: {
            title: 'Fila de Atendimento',
            description: 'Consulte a fila operacional completa e localize rapidamente cada atendimento.'
        },
        GERENTE: {
            title: 'Chamados',
            description: 'Visão completa dos chamados, responsáveis, classificações e prazos da operação.'
        }
    }[role];

    if (pageCopy) {
        elements.pageTitle.textContent = pageCopy.title;
        elements.pageDescription.textContent = pageCopy.description;
        document.title = `${pageCopy.title} — Speed Desk`;
    }

    function setSelectValue(select, value) {
        if (value && [...select.options].some(option => option.value === value)) {
            select.value = value;
        }
    }

    function hydrateFiltersFromUrl() {
        const params = new URLSearchParams(window.location.search);
        elements.query.value = params.get('query') || '';
        setSelectValue(elements.status, params.get('status'));
        setSelectValue(elements.priority, params.get('prioridade'));
        setSelectValue(elements.type, params.get('ticketType'));
        state.initialCategoryId = params.get('categoryId') || '';
        state.initialTechnicianId = params.get('semTecnico') === 'true'
            ? UNASSIGNED_FILTER
            : (params.get('tecnicoId') || '');
    }

    function renderCategoryOptions() {
        const previousValue = elements.category.value || state.initialCategoryId;
        const selectedType = elements.type.value;
        const matchingCategories = state.categories.filter(category => (
            category.active !== false && (!selectedType || category.ticketType === selectedType)
        ));

        const options = [new Option('Todas', '')];
        matchingCategories.forEach(category => {
            const typeSuffix = selectedType ? '' : ` · ${TYPE_LABELS[category.ticketType] || category.ticketType}`;
            options.push(new Option(`${category.name}${typeSuffix}`, category.id));
        });
        elements.category.replaceChildren(...options);
        elements.category.disabled = false;
        setSelectValue(elements.category, previousValue);
        state.initialCategoryId = '';
    }

    async function loadCategories() {
        elements.category.disabled = true;
        elements.category.replaceChildren(new Option('Carregando...', ''));
        try {
            const response = await api.request('/ticket-categories');
            state.categories = Array.isArray(response) ? response : [];
        } catch (error) {
            console.error('Erro ao carregar categorias dos filtros:', error);
            state.categories = [];
        } finally {
            renderCategoryOptions();
        }
    }

    function renderTechnicianOptions() {
        const previousValue = elements.technician.value || state.initialTechnicianId;
        const technicians = [...state.technicians.values()]
            .sort((left, right) => left.name.localeCompare(right.name, 'pt-BR'));

        const options = [
            new Option('Todos', ''),
            new Option('Sem responsável', UNASSIGNED_FILTER)
        ];
        technicians.forEach(technician => {
            const ownSuffix = technician.id === session.id ? ' (você)' : '';
            options.push(new Option(`${technician.name}${ownSuffix}`, technician.id));
        });
        elements.technician.replaceChildren(...options);
        setSelectValue(elements.technician, previousValue);
        state.initialTechnicianId = '';
    }

    function mergeTechnicians(tickets) {
        tickets.forEach(ticket => {
            if (ticket.tecnico?.id && ticket.tecnico?.name) {
                state.technicians.set(ticket.tecnico.id, ticket.tecnico);
            }
        });
        renderTechnicianOptions();
    }

    async function loadManagerTechnicians() {
        if (role !== 'GERENTE') return;
        try {
            const response = await api.request('/users');
            if (Array.isArray(response)) {
                response.filter(user => user.role === 'TECNICO').forEach(technician => {
                    state.technicians.set(technician.id, technician);
                });
                renderTechnicianOptions();
            }
        } catch (error) {
            console.error('Erro ao carregar responsáveis:', error);
        }
    }

    function buildRequestParams() {
        const params = new URLSearchParams();
        const technicianValue = elements.technician.value;
        const values = {
            query: elements.query.value.trim(),
            status: elements.status.value,
            prioridade: elements.priority.value,
            ticketType: elements.type.value,
            categoryId: elements.category.value,
            tecnicoId: technicianValue === UNASSIGNED_FILTER ? '' : technicianValue,
            semTecnico: technicianValue === UNASSIGNED_FILTER ? 'true' : ''
        };

        Object.entries(values).forEach(([key, value]) => {
            if (value) params.set(key, value);
        });
        return params;
    }

    function synchronizeUrl(params) {
        const queryString = params.toString();
        const nextUrl = `${window.location.pathname}${queryString ? `?${queryString}` : ''}`;
        window.history.replaceState({}, '', nextUrl);
    }

    function renderTableState(message, tone = '', retry = false) {
        elements.body.replaceChildren();
        const row = document.createElement('tr');
        row.className = `table-state-row ${tone}`.trim();
        const cell = document.createElement('td');
        cell.colSpan = 6;

        const content = document.createElement('div');
        content.className = 'catalog-state';
        const text = document.createElement('span');
        text.textContent = message;
        content.appendChild(text);

        if (retry) {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'btn btn-secondary btn-compact';
            button.textContent = 'Tentar novamente';
            button.addEventListener('click', () => loadTickets());
            content.appendChild(button);
        }

        cell.appendChild(content);
        row.appendChild(cell);
        elements.body.appendChild(row);
    }

    function createTextCell(label, primary, secondary = '') {
        const cell = document.createElement('td');
        cell.dataset.label = label;
        const primaryText = document.createElement('span');
        primaryText.className = 'ticket-cell-primary';
        primaryText.textContent = primary;
        cell.appendChild(primaryText);
        if (secondary) {
            const secondaryText = document.createElement('span');
            secondaryText.className = 'ticket-cell-secondary';
            secondaryText.textContent = secondary;
            cell.appendChild(secondaryText);
        }
        return cell;
    }

    function createTicketRow(ticket) {
        const detailHref = `chamado.html?id=${encodeURIComponent(ticket.id)}`;
        const row = document.createElement('tr');
        row.className = 'ticket-catalog-row';

        const codeCell = document.createElement('td');
        codeCell.dataset.label = 'Código';
        const codeLink = document.createElement('a');
        codeLink.className = 'ticket-table-code';
        codeLink.href = detailHref;
        codeLink.textContent = getTicketCode(ticket);
        codeCell.appendChild(codeLink);

        const ticketCell = document.createElement('td');
        ticketCell.dataset.label = 'Chamado';
        ticketCell.className = 'ticket-table-primary';
        const titleLink = document.createElement('a');
        titleLink.className = 'ticket-table-title';
        titleLink.href = detailHref;
        titleLink.textContent = ticket.titulo || 'Chamado sem título';
        const description = document.createElement('span');
        description.className = 'ticket-table-description';
        description.textContent = ticket.descricao || 'Sem descrição.';
        ticketCell.append(titleLink, description);

        const clientCell = createTextCell(
            'Solicitante',
            ticket.cliente?.name || 'Não informado',
            ticket.cliente?.organization?.name || ticket.cliente?.email || ''
        );
        const technicianCell = createTextCell(
            'Responsável',
            ticket.tecnico?.name || 'Não atribuído',
            ticket.tecnico?.email || ''
        );

        const classificationCell = document.createElement('td');
        classificationCell.dataset.label = 'Classificação';
        const classification = document.createElement('div');
        classification.className = 'ticket-table-badges';
        classification.append(
            createBadge(TYPE_LABELS[ticket.ticketType] || ticket.ticketType || 'Geral', 'badge-ticket-type'),
            createBadge(PRIORITY_LABELS[ticket.prioridade] || ticket.prioridade || 'Baixa', priorityClass(ticket.prioridade))
        );
        if (ticket.category?.name) {
            classification.appendChild(createBadge(ticket.category.name, 'badge-category'));
        }
        classificationCell.appendChild(classification);

        const statusCell = document.createElement('td');
        statusCell.dataset.label = 'Status e SLA';
        const statusContent = document.createElement('div');
        statusContent.className = 'ticket-table-status';
        statusContent.appendChild(createBadge(
            STATUS_LABELS[ticket.status] || ticket.status || 'Recebido',
            `badge-status ${statusClass(ticket.status)}`
        ));
        const slaInfo = getSlaInfo(ticket);
        const sla = document.createElement('span');
        sla.className = `catalog-sla ${slaInfo.tone}`.trim();
        sla.textContent = slaInfo.label;
        sla.title = `Vencimento: ${formatDate(ticket.dataVencimento)}`;
        statusContent.appendChild(sla);
        statusCell.appendChild(statusContent);

        row.append(codeCell, ticketCell, clientCell, technicianCell, classificationCell, statusCell);
        row.addEventListener('click', event => {
            if (event.target.closest('a, button, input, select, textarea')) return;
            window.location.href = detailHref;
        });
        return row;
    }

    function renderTickets(tickets, activeFilterCount) {
        elements.body.replaceChildren();
        if (tickets.length === 0) {
            renderTableState(
                activeFilterCount > 0
                    ? 'Nenhum chamado corresponde aos filtros informados.'
                    : 'Nenhum chamado disponível para o seu perfil.',
                'empty'
            );
            return;
        }

        const fragment = document.createDocumentFragment();
        tickets.forEach(ticket => fragment.appendChild(createTicketRow(ticket)));
        elements.body.appendChild(fragment);
    }

    async function loadTickets() {
        window.clearTimeout(state.searchTimer);
        state.requestController?.abort();
        state.requestController = new AbortController();
        const requestId = ++state.requestSequence;
        const params = buildRequestParams();
        const activeFilterCount = [...params.keys()].length;
        synchronizeUrl(params);

        elements.body.setAttribute('aria-busy', 'true');
        elements.total.textContent = 'Carregando';
        elements.summary.textContent = 'Consultando a fila...';
        renderTableState('Carregando chamados...');

        try {
            const suffix = params.size > 0 ? `?${params.toString()}` : '';
            const response = await api.request(`/tickets${suffix}`, {
                signal: state.requestController.signal
            });
            if (requestId !== state.requestSequence) return;

            const tickets = Array.isArray(response) ? response : [];
            mergeTechnicians(tickets);
            renderTickets(tickets, activeFilterCount);
            elements.total.textContent = `${tickets.length} encontrado${tickets.length === 1 ? '' : 's'}`;
            elements.summary.textContent = activeFilterCount > 0
                ? `${tickets.length} chamado(s) encontrado(s) com ${activeFilterCount} filtro(s) ativo(s).`
                : `${tickets.length} chamado(s) disponível(is), ordenado(s) pelos dados da API.`;
            if (activeFilterCount === 0) updateTicketNavigationCount(tickets.length);
        } catch (error) {
            if (error.name === 'AbortError') return;
            console.error('Erro ao carregar chamados:', error);
            elements.total.textContent = 'Indisponível';
            elements.summary.textContent = 'A consulta não pôde ser concluída.';
            renderTableState(error.message || 'Não foi possível carregar os chamados.', 'error', true);
        } finally {
            if (requestId === state.requestSequence) {
                elements.body.setAttribute('aria-busy', 'false');
            }
        }
    }

    elements.form.addEventListener('submit', event => {
        event.preventDefault();
        loadTickets();
    });

    elements.query.addEventListener('input', () => {
        window.clearTimeout(state.searchTimer);
        state.searchTimer = window.setTimeout(loadTickets, 350);
    });

    [elements.status, elements.priority, elements.category, elements.technician].forEach(select => {
        select.addEventListener('change', loadTickets);
    });

    elements.type.addEventListener('change', () => {
        renderCategoryOptions();
        loadTickets();
    });

    elements.clear.addEventListener('click', () => {
        elements.form.reset();
        state.initialCategoryId = '';
        state.initialTechnicianId = '';
        renderCategoryOptions();
        renderTechnicianOptions();
        loadTickets();
        elements.query.focus();
    });

    async function initialize() {
        hydrateFiltersFromUrl();
        await Promise.allSettled([loadCategories(), loadManagerTechnicians()]);
        renderTechnicianOptions();
        await loadTickets();
    }

    initialize();
});
