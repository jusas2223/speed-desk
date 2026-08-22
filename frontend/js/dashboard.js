import api from './api.js';
import { updateTicketNavigationCount } from './navigation.js';

const TICKET_TYPE_LABELS = Object.freeze({
    GERAL: 'Geral',
    HARDWARE: 'Hardware',
    SOFTWARE: 'Software'
});

const STATUS_LABELS = Object.freeze({
    RECEBIDO: 'Recebido',
    EM_TRIAGEM: 'Em triagem',
    EM_ATENDIMENTO: 'Em atendimento',
    AGUARDANDO_CLIENTE: 'Aguardando cliente',
    AGUARDANDO_PECA: 'Aguardando peça',
    RESOLVIDO: 'Resolvido',
    FECHADO: 'Fechado'
});

const CLOSED_STATUSES = new Set(['RESOLVIDO', 'FECHADO']);
const STATUS_GROUPS = Object.freeze([
    { label: 'Resolvidos', statuses: ['RESOLVIDO', 'FECHADO'], color: 'var(--success)' },
    { label: 'Em atendimento', statuses: ['EM_ATENDIMENTO'], color: 'var(--brand-primary)' },
    { label: 'Abertos na fila', statuses: ['RECEBIDO', 'EM_TRIAGEM'], color: 'var(--info)' },
    { label: 'Pendentes / Aguardando', statuses: ['AGUARDANDO_CLIENTE', 'AGUARDANDO_PECA'], color: 'var(--warning)' }
]);

document.addEventListener('DOMContentLoaded', () => {
    const session = api.requireAuth();
    if (!session) return;

    const role = String(session.role || '').toUpperCase();
    const firstName = String(session.name || 'Usuário').trim().split(/\s+/)[0];

    const elements = {
        welcome: document.getElementById('welcomeMessage'),
        headerActions: document.getElementById('headerActions'),
        search: document.getElementById('ticketSearch'),
        ticketList: document.getElementById('ticketList'),
        ticketCountLabel: document.getElementById('ticketCountLabel'),
        metricOpen: document.getElementById('metricOpen'),
        metricOpenTrend: document.getElementById('metricOpenTrend'),
        metricOpenSubtitle: document.getElementById('metricOpenSubtitle'),
        metricInProgress: document.getElementById('metricInProgress'),
        metricInProgressTrend: document.getElementById('metricInProgressTrend'),
        metricRisk: document.getElementById('metricRisk'),
        metricRiskTrend: document.getElementById('metricRiskTrend'),
        metricResolvedToday: document.getElementById('metricResolvedToday'),
        statusTotal: document.getElementById('statusTotal'),
        statusSegments: document.getElementById('statusSegments'),
        statusBreakdown: document.getElementById('statusBreakdown'),
        categoryDistribution: document.getElementById('categoryDistribution'),
        toastRegion: document.getElementById('toastRegion')
    };

    const state = {
        tickets: [],
        activeFilter: 'TODOS',
        query: ''
    };

    elements.headerActions.hidden = role !== 'CLIENTE';

    function showToast(message, tone = '') {
        const toast = document.createElement('div');
        toast.className = `toast ${tone}`.trim();
        toast.textContent = message;
        elements.toastRegion.appendChild(toast);
        window.setTimeout(() => toast.remove(), 3600);
    }

    function createIcon(paths) {
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('class', 'icon');
        svg.setAttribute('viewBox', '0 0 24 24');
        svg.setAttribute('aria-hidden', 'true');
        svg.innerHTML = paths;
        return svg;
    }

    function createBadge(label, className) {
        const badge = document.createElement('span');
        badge.className = `badge ${className}`;
        badge.textContent = label;
        return badge;
    }

    function getInitials(name) {
        const words = String(name || 'Cliente').trim().split(/\s+/).filter(Boolean);
        if (words.length === 0) return 'CL';
        return (words[0][0] + (words.length > 1 ? words.at(-1)[0] : words[0][1] || '')).toUpperCase();
    }

    function getTicketCode(ticket) {
        const compactId = String(ticket.id || '').replaceAll('-', '').slice(0, 6).toUpperCase();
        return `SPD-${compactId || '000000'}`;
    }

    function parseDate(value) {
        if (!value) return null;
        const parsed = new Date(value);
        return Number.isNaN(parsed.getTime()) ? null : parsed;
    }

    function isToday(value) {
        const date = parseDate(value);
        if (!date) return false;
        const today = new Date();
        return date.getFullYear() === today.getFullYear()
            && date.getMonth() === today.getMonth()
            && date.getDate() === today.getDate();
    }

    function formatRelativeTime(value) {
        const date = parseDate(value);
        if (!date) return 'Data indisponível';

        const seconds = Math.max(0, Math.floor((Date.now() - date.getTime()) / 1000));
        if (seconds < 60) return 'Agora';
        if (seconds < 3600) return `Há ${Math.floor(seconds / 60)} min`;
        if (seconds < 86400) return `Há ${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}min`;
        return `Há ${Math.floor(seconds / 86400)} dia(s)`;
    }

    function getDeadlineInfo(ticket) {
        const hasServerRemaining = ticket.slaRemainingSeconds !== null
            && ticket.slaRemainingSeconds !== undefined
            && Number.isFinite(Number(ticket.slaRemainingSeconds));
        const remainingSeconds = hasServerRemaining
            ? Math.abs(Number(ticket.slaRemainingSeconds))
            : 0;

        function formatServerDuration(seconds) {
            const totalMinutes = Math.max(1, Math.ceil(seconds / 60));
            if (totalMinutes < 60) return `${totalMinutes}min`;
            if (totalMinutes < 1440) {
                const hours = Math.floor(totalMinutes / 60);
                const minutes = totalMinutes % 60;
                return minutes ? `${hours}h ${minutes}min` : `${hours}h`;
            }
            const days = Math.floor(totalMinutes / 1440);
            const hours = Math.floor((totalMinutes % 1440) / 60);
            return hours ? `${days}d ${hours}h` : `${days}d`;
        }

        if (ticket.slaState === 'PAUSED' || ticket.slaPaused) {
            return {
                label: hasServerRemaining
                    ? (Number(ticket.slaRemainingSeconds) >= 0
                        ? `SLA pausado · ${formatServerDuration(remainingSeconds)} preservados`
                        : `SLA pausado · vencido há ${formatServerDuration(remainingSeconds)}`)
                    : 'SLA pausado',
                risk: false,
                tone: 'is-paused'
            };
        }
        if (ticket.slaState === 'MET') return { label: 'SLA cumprido', risk: false, tone: 'is-complete' };
        if (ticket.slaState === 'BREACHED') {
            return {
                label: hasServerRemaining
                    ? `Prazo vencido há ${formatServerDuration(remainingSeconds)}`
                    : 'Prazo vencido',
                risk: true,
                tone: 'is-overdue'
            };
        }
        if (ticket.slaState === 'AT_RISK') {
            return {
                label: hasServerRemaining
                    ? `Prazo: ${formatServerDuration(remainingSeconds)} restantes`
                    : 'SLA em risco',
                risk: true,
                tone: 'is-risk'
            };
        }
        if (ticket.slaState === 'ON_TRACK') {
            return {
                label: hasServerRemaining
                    ? `Prazo: ${formatServerDuration(remainingSeconds)} restantes`
                    : 'Dentro do prazo',
                risk: false,
                tone: ''
            };
        }

        if (CLOSED_STATUSES.has(ticket.status)) {
            return { label: 'Finalizado', risk: false, tone: 'is-complete' };
        }

        const deadline = parseDate(ticket.dataVencimento);
        if (!deadline) return { label: 'Prazo não informado', risk: false };

        const difference = deadline.getTime() - Date.now();
        const absoluteMinutes = Math.ceil(Math.abs(difference) / 60000);
        const risk = difference <= 24 * 60 * 60 * 1000;

        if (difference <= 0) {
            if (absoluteMinutes < 60) return { label: `Prazo vencido há ${absoluteMinutes}min`, risk: true };
            if (absoluteMinutes < 1440) return { label: `Prazo vencido há ${Math.floor(absoluteMinutes / 60)}h`, risk: true };
            return { label: `Prazo vencido há ${Math.floor(absoluteMinutes / 1440)}d`, risk: true };
        }

        if (absoluteMinutes < 60) return { label: `Prazo: ${absoluteMinutes}min restantes`, risk };
        if (absoluteMinutes < 1440) {
            const hours = Math.floor(absoluteMinutes / 60);
            const minutes = absoluteMinutes % 60;
            return { label: `Prazo: ${hours}h ${minutes}min restantes`, risk };
        }
        return { label: `Prazo: ${Math.ceil(absoluteMinutes / 1440)}d restantes`, risk };
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

    function createActionButton(label, tone, handler) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = `btn btn-compact ${tone}`;
        button.textContent = label;
        button.addEventListener('click', async event => {
            event.stopPropagation();
            button.disabled = true;
            try {
                await handler();
            } finally {
                if (button.isConnected) button.disabled = false;
            }
        });
        return button;
    }

    function createTicketRow(ticket) {
        const row = document.createElement('article');
        const detailHref = `chamado.html?id=${encodeURIComponent(ticket.id)}`;
        row.className = 'ticket-row is-clickable';
        row.title = ticket.descricao || ticket.titulo;

        const main = document.createElement('div');
        main.className = 'ticket-main';

        const avatar = document.createElement('span');
        avatar.className = 'ticket-avatar';
        avatar.textContent = getInitials(ticket.cliente?.name);

        const copy = document.createElement('div');
        copy.className = 'ticket-copy';

        const labels = document.createElement('div');
        labels.className = 'ticket-labels';

        const code = document.createElement('span');
        code.className = 'ticket-code';
        code.textContent = getTicketCode(ticket);
        code.title = String(ticket.id || '');
        labels.appendChild(code);

        const type = ticket.ticketType || 'GERAL';
        labels.appendChild(createBadge(
            ticket.category?.name || TICKET_TYPE_LABELS[type] || type,
            'badge-category'
        ));
        labels.appendChild(createBadge(
            String(ticket.prioridade || 'BAIXA').toLowerCase().replace(/^./, value => value.toUpperCase()),
            priorityClass(ticket.prioridade)
        ));

        const title = document.createElement('a');
        title.className = 'ticket-title ticket-title-link';
        title.href = detailHref;
        title.textContent = ticket.titulo || 'Chamado sem título';

        const meta = document.createElement('div');
        meta.className = 'ticket-meta';

        const metaItems = [
            ticket.cliente?.name || 'Cliente não informado',
            ticket.cliente?.organization?.name,
            ticket.asset?.nome,
            formatRelativeTime(ticket.dataCriacao)
        ].filter(Boolean);

        metaItems.forEach((value, index) => {
            if (index > 0) {
                const separator = document.createElement('span');
                separator.className = 'ticket-meta-separator';
                separator.textContent = '•';
                meta.appendChild(separator);
            }
            const item = document.createElement('span');
            item.textContent = value;
            meta.appendChild(item);
        });

        copy.append(labels, title, meta);
        main.append(avatar, copy);

        const side = document.createElement('div');
        side.className = 'ticket-side';

        const statusStack = document.createElement('div');
        statusStack.className = 'ticket-status-stack';
        statusStack.appendChild(createBadge(
            STATUS_LABELS[ticket.status] || ticket.status || 'Recebido',
            `badge-status ${statusClass(ticket.status)}`
        ));

        const deadlineInfo = getDeadlineInfo(ticket);
        const deadline = document.createElement('span');
        deadline.className = `ticket-sla ${deadlineInfo.tone || (deadlineInfo.risk ? 'is-risk' : '')}`.trim();
        deadline.textContent = deadlineInfo.label;
        statusStack.appendChild(deadline);
        side.appendChild(statusStack);

        const actions = document.createElement('div');
        actions.className = 'ticket-actions';

        if (ticket.status === 'RECEBIDO' && role === 'TECNICO') {
            actions.appendChild(createActionButton('Assumir', 'btn-primary', () => assumeTicket(ticket.id)));
        }
        if (ticket.status === 'RECEBIDO' && role === 'GERENTE') {
            actions.appendChild(createActionButton('Atribuir', 'btn-primary', async () => openAssignModal(ticket.id)));
        }
        const technicianOwnsTicket = ticket.tecnico?.id === session.id;
        if (
            ticket.status === 'EM_ATENDIMENTO'
            && (role === 'GERENTE' || (role === 'TECNICO' && technicianOwnsTicket))
        ) {
            actions.appendChild(createActionButton('Resolver', 'btn-success', () => resolveTicket(ticket.id)));
        }

        if (actions.childElementCount > 0) side.appendChild(actions);
        row.append(main, side);
        row.addEventListener('click', event => {
            if (event.target.closest('a, button, input, select, textarea')) return;
            window.location.href = detailHref;
        });
        return row;
    }

    function getFilteredTickets() {
        const normalizedQuery = state.query.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

        return state.tickets.filter(ticket => {
            const searchText = [
                getTicketCode(ticket),
                ticket.titulo,
                ticket.descricao,
                ticket.cliente?.name,
                ticket.cliente?.organization?.name,
                ticket.category?.name,
                ticket.ticketType
            ].filter(Boolean).join(' ').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

            const matchesQuery = !normalizedQuery || searchText.includes(normalizedQuery);
            const matchesFilter = state.activeFilter === 'TODOS'
                || (state.activeFilter === 'CRITICOS' && ['ALTA', 'CRITICA'].includes(ticket.prioridade))
                || ticket.ticketType === state.activeFilter;

            return matchesQuery && matchesFilter;
        });
    }

    function renderTicketList() {
        const filteredTickets = getFilteredTickets();
        elements.ticketList.replaceChildren();

        elements.ticketCountLabel.textContent = filteredTickets.length === state.tickets.length
            ? `${state.tickets.length} chamado(s) carregado(s) com dados atuais.`
            : `${filteredTickets.length} de ${state.tickets.length} chamado(s) correspondem ao filtro.`;

        if (filteredTickets.length === 0) {
            const emptyState = document.createElement('div');
            emptyState.className = 'empty-state';

            const inner = document.createElement('div');
            inner.className = 'empty-state-inner';

            const icon = document.createElement('span');
            icon.className = 'empty-state-icon';
            icon.appendChild(createIcon('<path d="M4 5h16v14H4z"></path><path d="M8 9h8M8 13h5"></path>'));

            const title = document.createElement('strong');
            title.textContent = state.tickets.length === 0
                ? 'Nenhum chamado registrado'
                : 'Nenhum chamado encontrado';

            const description = document.createElement('span');
            description.textContent = state.tickets.length === 0
                ? 'A fila será exibida aqui assim que o primeiro chamado for criado.'
                : 'Altere a pesquisa ou escolha outro filtro.';

            inner.append(icon, title, description);
            emptyState.appendChild(inner);
            elements.ticketList.appendChild(emptyState);
            return;
        }

        filteredTickets.forEach(ticket => elements.ticketList.appendChild(createTicketRow(ticket)));
    }

    function renderMetrics() {
        const openTickets = state.tickets.filter(ticket => !CLOSED_STATUSES.has(ticket.status));
        const inProgress = state.tickets.filter(ticket => ticket.status === 'EM_ATENDIMENTO');
        const riskTickets = openTickets.filter(ticket => {
            if (ticket.slaState) return ['AT_RISK', 'BREACHED'].includes(ticket.slaState);
            const riskLimit = Date.now() + 24 * 60 * 60 * 1000;
            const deadline = parseDate(ticket.dataVencimento);
            return deadline && deadline.getTime() <= riskLimit;
        });
        const resolvedToday = state.tickets.filter(ticket => (
            CLOSED_STATUSES.has(ticket.status)
            && isToday(ticket.closedAt || ticket.resolvedAt || ticket.dataAtualizacao)
        ));
        const received = state.tickets.filter(ticket => ['RECEBIDO', 'EM_TRIAGEM'].includes(ticket.status));

        elements.metricOpen.textContent = String(openTickets.length);
        elements.metricOpenTrend.textContent = received.length > 0
            ? `${received.length} na fila`
            : 'Fila atual';
        elements.metricOpenSubtitle.textContent = openTickets.length > 0
            ? 'Chamados ainda não finalizados'
            : 'Nenhum chamado ativo no momento';

        elements.metricInProgress.textContent = String(inProgress.length);
        elements.metricInProgressTrend.textContent = inProgress.length === 1 ? '1 em curso' : `${inProgress.length} em curso`;
        elements.metricRisk.textContent = String(riskTickets.length);
        elements.metricRiskTrend.textContent = riskTickets.some(ticket => (
            ticket.slaState === 'BREACHED'
            || (!ticket.slaState && parseDate(ticket.dataVencimento)?.getTime() < Date.now())
        ))
            ? 'Inclui vencidos'
            : 'Próximas 24h';
        elements.metricResolvedToday.textContent = String(resolvedToday.length);
    }

    function renderStatusDistribution() {
        elements.statusSegments.replaceChildren();
        elements.statusBreakdown.replaceChildren();
        elements.statusTotal.textContent = `${state.tickets.length} total`;

        const denominator = Math.max(1, state.tickets.length);
        STATUS_GROUPS.forEach(group => {
            const count = state.tickets.filter(ticket => group.statuses.includes(ticket.status)).length;
            const percentage = state.tickets.length === 0 ? 0 : Math.round((count / denominator) * 100);

            const segment = document.createElement('span');
            segment.className = 'status-segment';
            segment.style.width = `${percentage}%`;
            segment.style.backgroundColor = group.color;
            elements.statusSegments.appendChild(segment);

            const item = document.createElement('li');
            item.className = 'status-breakdown-item';

            const label = document.createElement('span');
            label.className = 'status-breakdown-label';
            const dot = document.createElement('span');
            dot.className = 'status-dot';
            dot.style.backgroundColor = group.color;
            const labelText = document.createElement('span');
            labelText.textContent = group.label;
            label.append(dot, labelText);

            const value = document.createElement('span');
            value.className = 'status-breakdown-value';
            value.textContent = `${count} (${percentage}%)`;

            item.append(label, value);
            elements.statusBreakdown.appendChild(item);
        });
    }

    function renderCategoryDistribution() {
        const counts = new Map();
        state.tickets.forEach(ticket => {
            const category = ticket.category?.name || TICKET_TYPE_LABELS[ticket.ticketType] || 'Geral';
            counts.set(category, (counts.get(category) || 0) + 1);
        });

        elements.categoryDistribution.replaceChildren();
        const categories = [...counts.entries()].sort((left, right) => right[1] - left[1]).slice(0, 4);

        if (categories.length === 0) {
            const empty = document.createElement('div');
            empty.className = 'category-summary';
            empty.style.gridColumn = '1 / -1';
            const label = document.createElement('span');
            label.className = 'category-summary-name';
            label.textContent = 'Sem dados disponíveis';
            const count = document.createElement('strong');
            count.className = 'category-summary-count';
            count.textContent = '0 chamados';
            empty.append(label, count);
            elements.categoryDistribution.appendChild(empty);
            return;
        }

        categories.forEach(([name, count]) => {
            const item = document.createElement('div');
            item.className = 'category-summary';

            const label = document.createElement('span');
            label.className = 'category-summary-name';
            label.textContent = name;

            const value = document.createElement('strong');
            value.className = 'category-summary-count';
            value.textContent = `${count} chamado${count === 1 ? '' : 's'}`;

            item.append(label, value);
            elements.categoryDistribution.appendChild(item);
        });
    }

    function renderDashboard() {
        updateTicketNavigationCount(state.tickets.length);
        renderMetrics();
        renderTicketList();
        renderStatusDistribution();
        renderCategoryDistribution();

        const openCount = state.tickets.filter(ticket => !CLOSED_STATUSES.has(ticket.status)).length;
        elements.welcome.textContent = state.tickets.length === 0
            ? `Olá, ${firstName}. A operação está pronta para receber chamados.`
            : `Olá, ${firstName}. ${openCount} chamado(s) ativo(s) em uma fila de ${state.tickets.length}.`;
    }

    async function loadTickets() {
        elements.ticketList.innerHTML = '<div class="loading-state">Carregando chamados...</div>';
        try {
            const response = await api.request('/tickets');
            state.tickets = Array.isArray(response) ? response : [];
            renderDashboard();
        } catch (error) {
            console.error('Erro ao carregar chamados:', error);
            elements.ticketList.innerHTML = '<div class="error-state">Não foi possível carregar a fila de chamados.</div>';
            elements.ticketCountLabel.textContent = 'Falha ao sincronizar os dados.';
            showToast(error.message || 'Falha ao carregar chamados.', 'error');
        }
    }

    elements.search.addEventListener('input', event => {
        state.query = event.target.value.trim();
        renderTicketList();
    });

    document.querySelectorAll('[data-ticket-filter]').forEach(button => {
        button.addEventListener('click', () => {
            document.querySelectorAll('[data-ticket-filter]').forEach(item => item.classList.remove('is-active'));
            button.classList.add('is-active');
            state.activeFilter = button.dataset.ticketFilter;
            renderTicketList();
        });
    });

    async function assumeTicket(ticketId) {
        if (role !== 'TECNICO') return;
        try {
            await api.request(`/tickets/${ticketId}/assumir/${session.id}`, { method: 'PATCH' });
            showToast('Chamado assumido com sucesso.', 'success');
            await loadTickets();
        } catch (error) {
            showToast(error.message || 'Falha ao assumir chamado.', 'error');
        }
    }

    async function resolveTicket(ticketId) {
        try {
            await api.request(`/tickets/${ticketId}/status`, {
                method: 'PATCH',
                body: JSON.stringify({ status: 'RESOLVIDO' })
            });
            showToast('Chamado resolvido com sucesso.', 'success');
            await loadTickets();
        } catch (error) {
            showToast(error.message || 'Falha ao resolver chamado.', 'error');
        }
    }

    const assignModal = document.getElementById('assignModal');
    const assignForm = document.getElementById('assignForm');
    const assignTicketId = document.getElementById('assignTicketId');
    const technicianSelect = document.getElementById('technicianSelect');
    const submitAssignButton = document.getElementById('btnSubmitAssign');
    let techniciansLoaded = false;

    function setAssignButtonState() {
        submitAssignButton.disabled = !technicianSelect.value;
    }

    async function openAssignModal(ticketId) {
        if (role !== 'GERENTE') return;

        assignTicketId.value = ticketId;
        assignModal.hidden = false;
        assignModal.removeAttribute('inert');
        assignModal.setAttribute('aria-hidden', 'false');
        assignModal.classList.add('active');
        technicianSelect.focus();

        if (techniciansLoaded) {
            setAssignButtonState();
            return;
        }

        technicianSelect.replaceChildren(new Option('Carregando técnicos...', ''));
        technicianSelect.disabled = true;
        setAssignButtonState();

        try {
            const users = await api.request('/users');
            const technicians = Array.isArray(users)
                ? users.filter(user => user.role === 'TECNICO' && user.active !== false)
                : [];

            const options = [new Option('Selecione um técnico', '')];
            technicians.forEach(technician => {
                options.push(new Option(`${technician.name} (${technician.email})`, technician.id));
            });
            technicianSelect.replaceChildren(...options);
            techniciansLoaded = true;
        } catch (error) {
            technicianSelect.replaceChildren(new Option('Erro ao carregar técnicos', ''));
            showToast(error.message || 'Falha ao carregar técnicos.', 'error');
        } finally {
            technicianSelect.disabled = false;
            setAssignButtonState();
        }
    }

    function closeAssignModal() {
        assignModal.classList.remove('active');
        assignModal.setAttribute('aria-hidden', 'true');
        assignModal.setAttribute('inert', '');
        assignModal.hidden = true;
        assignForm.reset();
        setAssignButtonState();
    }

    technicianSelect.addEventListener('change', setAssignButtonState);
    document.getElementById('btnCloseAssignModal').addEventListener('click', closeAssignModal);
    document.getElementById('btnCancelAssignModal').addEventListener('click', closeAssignModal);
    assignModal.addEventListener('click', event => {
        if (event.target === assignModal) closeAssignModal();
    });

    assignForm.addEventListener('submit', async event => {
        event.preventDefault();
        if (role !== 'GERENTE' || !technicianSelect.value) return;

        submitAssignButton.disabled = true;
        const defaultText = submitAssignButton.textContent;
        submitAssignButton.textContent = 'Atribuindo...';

        try {
            await api.request(
                `/tickets/${assignTicketId.value}/assumir/${technicianSelect.value}`,
                { method: 'PATCH' }
            );
            closeAssignModal();
            showToast('Técnico atribuído com sucesso.', 'success');
            await loadTickets();
        } catch (error) {
            showToast(error.message || 'Falha ao atribuir técnico.', 'error');
        } finally {
            submitAssignButton.textContent = defaultText;
            setAssignButtonState();
        }
    });

    const newTicketModal = document.getElementById('newTicketModal');
    const newTicketForm = document.getElementById('newTicketForm');
    const submitTicketButton = document.getElementById('btnSubmitTicket');
    const assetSelect = document.getElementById('assetSelect');
    const ticketTypeSelect = document.getElementById('ticketType');
    const categorySelect = document.getElementById('categorySelect');
    const categoryStatus = document.getElementById('categoryStatus');
    let ticketCategories = [];
    let categoriesLoaded = false;

    function option(value, label) {
        return new Option(label, value);
    }

    function renderCategoryOptions() {
        const selectedType = ticketTypeSelect.value || 'GERAL';
        const compatibleCategories = ticketCategories.filter(category => (
            category.active !== false && category.ticketType === selectedType
        ));
        categorySelect.replaceChildren(
            option('', 'Sem categoria'),
            ...compatibleCategories.map(category => option(category.id, category.name))
        );
        categorySelect.disabled = false;
        categoryStatus.textContent = compatibleCategories.length === 0
            ? 'Nenhuma categoria ativa disponível para este tipo.'
            : '';
        categoryStatus.classList.remove('error');
    }

    async function loadCategories() {
        categorySelect.disabled = true;
        categoryStatus.textContent = 'Carregando categorias...';

        try {
            const response = await api.request('/ticket-categories');
            ticketCategories = Array.isArray(response) ? response : [];
            categoriesLoaded = true;
            renderCategoryOptions();
        } catch (error) {
            ticketCategories = [];
            categorySelect.replaceChildren(option('', 'Sem categoria'));
            categorySelect.disabled = false;
            categoryStatus.textContent = 'Não foi possível carregar as categorias. O chamado pode ser aberto sem categoria.';
            categoryStatus.classList.add('error');
        }
    }

    async function loadAssets() {
        assetSelect.disabled = true;
        assetSelect.replaceChildren(option('', 'Carregando equipamentos...'));
        try {
            const response = await api.request(`/assets/cliente/${session.id}`);
            const assets = Array.isArray(response) ? response : [];
            assetSelect.replaceChildren(
                option('', 'Nenhum / Não listado'),
                ...assets.map(asset => option(asset.id, `${asset.nome} (${asset.numeroSerie || 'Sem NS'})`))
            );
        } catch {
            assetSelect.replaceChildren(option('', 'Nenhum / Não listado'));
        } finally {
            assetSelect.disabled = false;
        }
    }

    function openNewTicketModal() {
        if (role !== 'CLIENTE') return;
        newTicketModal.hidden = false;
        newTicketModal.removeAttribute('inert');
        newTicketModal.setAttribute('aria-hidden', 'false');
        newTicketModal.classList.add('active');
        document.getElementById('titulo').focus();
        loadAssets();
        if (categoriesLoaded) renderCategoryOptions();
        else loadCategories();
    }

    function closeNewTicketModal() {
        newTicketModal.classList.remove('active');
        newTicketModal.setAttribute('aria-hidden', 'true');
        newTicketModal.setAttribute('inert', '');
        newTicketModal.hidden = true;
        newTicketForm.reset();
        ticketTypeSelect.value = 'GERAL';
        if (categoriesLoaded) renderCategoryOptions();
    }

    document.getElementById('btnOpenModal').addEventListener('click', openNewTicketModal);
    document.getElementById('btnCloseModal').addEventListener('click', closeNewTicketModal);
    document.getElementById('btnCancelModal').addEventListener('click', closeNewTicketModal);
    ticketTypeSelect.addEventListener('change', () => {
        if (categoriesLoaded) renderCategoryOptions();
    });
    newTicketModal.addEventListener('click', event => {
        if (event.target === newTicketModal) closeNewTicketModal();
    });

    newTicketForm.addEventListener('submit', async event => {
        event.preventDefault();
        if (role !== 'CLIENTE') return;

        submitTicketButton.disabled = true;
        const defaultText = submitTicketButton.textContent;
        submitTicketButton.textContent = 'Salvando...';

        const payload = {
            titulo: document.getElementById('titulo').value.trim(),
            descricao: document.getElementById('descricao').value.trim(),
            prioridade: document.getElementById('prioridade').value,
            clienteId: session.id,
            assetId: assetSelect.value || null,
            ticketType: ticketTypeSelect.value || 'GERAL'
        };
        if (categorySelect.value) payload.categoryId = categorySelect.value;

        try {
            await api.request('/tickets', {
                method: 'POST',
                body: JSON.stringify(payload)
            });
            closeNewTicketModal();
            showToast('Chamado criado com sucesso.', 'success');
            await loadTickets();
        } catch (error) {
            showToast(error.message || 'Falha ao criar chamado.', 'error');
        } finally {
            submitTicketButton.disabled = false;
            submitTicketButton.textContent = defaultText;
        }
    });

    document.addEventListener('keydown', event => {
        if (event.key !== 'Escape') return;
        if (newTicketModal.classList.contains('active')) closeNewTicketModal();
        if (assignModal.classList.contains('active')) closeAssignModal();
    });

    loadTickets();
});
