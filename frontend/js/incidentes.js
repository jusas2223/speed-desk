import api from './api.js';

const STATUS_LABELS = Object.freeze({ ABERTO: 'Aberto', INVESTIGANDO: 'Investigando', MONITORANDO: 'Monitorando', RESOLVIDO: 'Resolvido' });
const SEVERITY_LABELS = Object.freeze({ BAIXA: 'Baixa', MEDIA: 'Média', ALTA: 'Alta', CRITICA: 'Crítica' });

document.addEventListener('DOMContentLoaded', () => {
    const session = api.requireAuth();
    if (!session) return;
    if (session.role === 'CLIENTE') {
        window.location.replace('dashboard.html');
        return;
    }

    const technician = session.role === 'TECNICO';
    const elements = {
        newButton: document.getElementById('newIncidentButton'),
        form: document.getElementById('incidentForm'),
        modal: document.getElementById('incidentModal'),
        modalTitle: document.getElementById('incidentModalTitle'),
        close: document.getElementById('closeIncidentModal'),
        cancel: document.getElementById('cancelIncident'),
        save: document.getElementById('saveIncident'),
        id: document.getElementById('incidentId'),
        title: document.getElementById('incidentTitle'),
        service: document.getElementById('incidentService'),
        startedAt: document.getElementById('incidentStartedAt'),
        severity: document.getElementById('incidentSeverity'),
        state: document.getElementById('incidentState'),
        description: document.getElementById('incidentDescription'),
        tickets: document.getElementById('incidentTickets'),
        feedback: document.getElementById('incidentFeedback'),
        filters: document.getElementById('incidentFilters'),
        query: document.getElementById('incidentQuery'),
        statusFilter: document.getElementById('incidentStatusFilter'),
        severityFilter: document.getElementById('incidentSeverityFilter'),
        clearFilters: document.getElementById('clearIncidentFilters'),
        list: document.getElementById('incidentList'),
        status: document.getElementById('incidentStatus'),
        toast: document.getElementById('incidentToastRegion')
    };
    const state = { incidents: [], tickets: [], timer: null, realtimeTimer: null };
    elements.newButton.hidden = !technician;

    function isoToLocalInput(value) {
        const date = value ? new Date(value) : new Date();
        const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
        return local.toISOString().slice(0, 16);
    }

    function formatDate(value) {
        if (!value) return 'Não informado';
        return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
    }

    function toast(message, error = false) {
        const item = document.createElement('div');
        item.className = `toast ${error ? 'toast-error' : 'toast-success'}`;
        item.textContent = message;
        elements.toast.appendChild(item);
        window.setTimeout(() => item.remove(), 4200);
    }

    function setFeedback(message = '', error = true) {
        elements.feedback.hidden = !message;
        elements.feedback.textContent = message;
        elements.feedback.className = `feedback ${error ? 'error' : 'success'}`;
    }

    function openModal(incident = null) {
        elements.form.reset();
        setFeedback();
        elements.id.value = incident?.id || '';
        elements.modalTitle.textContent = incident ? 'Editar incidente' : 'Novo incidente';
        elements.title.value = incident?.title || '';
        elements.service.value = incident?.affectedService || '';
        elements.startedAt.value = isoToLocalInput(incident?.startedAt);
        elements.severity.value = incident?.severity || 'MEDIA';
        elements.state.value = incident?.status || 'ABERTO';
        elements.description.value = incident?.description || '';
        const selected = new Set((incident?.tickets || []).map(ticket => ticket.id));
        [...elements.tickets.options].forEach(option => { option.selected = selected.has(option.value); });
        elements.modal.hidden = false;
        elements.modal.inert = false;
        elements.modal.setAttribute('aria-hidden', 'false');
        document.body.classList.add('modal-open');
        elements.title.focus();
    }

    function closeModal() {
        elements.modal.setAttribute('aria-hidden', 'true');
        elements.modal.inert = true;
        elements.modal.hidden = true;
        document.body.classList.remove('modal-open');
    }

    function renderTickets() {
        const options = state.tickets.map(ticket => new Option(
            `${ticketCode(ticket.id)} · ${ticket.titulo}`,
            ticket.id
        ));
        elements.tickets.replaceChildren(...options);
    }

    function ticketCode(id) {
        return `SPD-${String(id).replaceAll('-', '').slice(0, 6).toUpperCase()}`;
    }

    function badge(text, className) {
        const span = document.createElement('span');
        span.className = `badge ${className}`;
        span.textContent = text;
        return span;
    }

    function createCard(incident) {
        const card = document.createElement('article');
        card.className = `incident-card severity-${incident.severity.toLowerCase()}`;
        const head = document.createElement('div');
        head.className = 'incident-card-head';
        const copy = document.createElement('div');
        const eyebrow = document.createElement('div');
        eyebrow.className = 'incident-badges';
        eyebrow.append(
            badge(SEVERITY_LABELS[incident.severity], `incident-severity severity-${incident.severity.toLowerCase()}`),
            badge(STATUS_LABELS[incident.status], `incident-status status-${incident.status.toLowerCase()}`)
        );
        const title = document.createElement('h2');
        title.textContent = incident.title;
        const service = document.createElement('p');
        service.className = 'incident-service';
        service.textContent = incident.affectedService;
        copy.append(eyebrow, title, service);
        head.appendChild(copy);
        if (technician) {
            const edit = document.createElement('button');
            edit.type = 'button';
            edit.className = 'btn btn-secondary btn-compact';
            edit.textContent = 'Editar';
            edit.addEventListener('click', () => openModal(incident));
            head.appendChild(edit);
        }
        const description = document.createElement('p');
        description.className = 'incident-description';
        description.textContent = incident.description;
        const meta = document.createElement('div');
        meta.className = 'incident-meta';
        meta.innerHTML = `<span>Início <strong>${formatDate(incident.startedAt)}</strong></span><span>Responsável pelo registro <strong></strong></span><span>Chamados relacionados <strong>${incident.tickets.length}</strong></span>`;
        meta.querySelectorAll('strong')[1].textContent = incident.createdBy?.name || 'Não informado';
        card.append(head, description, meta);
        if (incident.tickets.length) {
            const links = document.createElement('div');
            links.className = 'incident-ticket-links';
            incident.tickets.forEach(ticket => {
                const link = document.createElement('a');
                link.href = `chamado.html?id=${encodeURIComponent(ticket.id)}`;
                link.textContent = ticket.code;
                links.appendChild(link);
            });
            card.appendChild(links);
        }
        return card;
    }

    function render() {
        elements.list.replaceChildren();
        elements.list.setAttribute('aria-busy', 'false');
        if (!state.incidents.length) {
            elements.status.className = 'list-status empty';
            elements.status.textContent = 'Nenhum incidente corresponde aos filtros atuais.';
            return;
        }
        elements.status.className = 'list-status ready';
        elements.status.textContent = `${state.incidents.length} incidente(s) encontrado(s).`;
        const fragment = document.createDocumentFragment();
        state.incidents.forEach(incident => fragment.appendChild(createCard(incident)));
        elements.list.appendChild(fragment);
    }

    async function loadIncidents() {
        const params = new URLSearchParams();
        if (elements.query.value.trim()) params.set('query', elements.query.value.trim());
        if (elements.statusFilter.value) params.set('status', elements.statusFilter.value);
        if (elements.severityFilter.value) params.set('severity', elements.severityFilter.value);
        elements.list.setAttribute('aria-busy', 'true');
        elements.status.className = 'list-status loading';
        elements.status.textContent = 'Carregando incidentes...';
        try {
            const suffix = params.size ? `?${params}` : '';
            state.incidents = await api.request(`/incidents${suffix}`);
            render();
        } catch (error) {
            elements.status.className = 'list-status error';
            elements.status.textContent = error.message;
        }
    }

    async function initialize() {
        try {
            state.tickets = await api.request('/tickets');
            renderTickets();
        } catch (error) {
            elements.tickets.replaceChildren(new Option('Chamados indisponíveis', ''));
            elements.tickets.disabled = true;
        }
        await loadIncidents();
    }

    elements.newButton.addEventListener('click', () => openModal());
    [elements.close, elements.cancel].forEach(button => button.addEventListener('click', closeModal));
    elements.modal.addEventListener('click', event => { if (event.target === elements.modal) closeModal(); });
    elements.form.addEventListener('submit', async event => {
        event.preventDefault();
        if (!elements.form.reportValidity()) return;
        elements.save.disabled = true;
        setFeedback();
        const payload = {
            title: elements.title.value.trim(),
            description: elements.description.value.trim(),
            affectedService: elements.service.value.trim(),
            severity: elements.severity.value,
            status: elements.state.value,
            startedAt: new Date(elements.startedAt.value).toISOString(),
            ticketIds: [...elements.tickets.selectedOptions].map(option => option.value).filter(Boolean)
        };
        try {
            const id = elements.id.value;
            await api.request(id ? `/incidents/${id}` : '/incidents', {
                method: id ? 'PUT' : 'POST',
                body: JSON.stringify(payload)
            });
            closeModal();
            toast(id ? 'Incidente atualizado.' : 'Incidente criado.');
            await loadIncidents();
        } catch (error) {
            setFeedback(error.message);
        } finally {
            elements.save.disabled = false;
        }
    });
    elements.filters.addEventListener('submit', event => { event.preventDefault(); loadIncidents(); });
    elements.query.addEventListener('input', () => { window.clearTimeout(state.timer); state.timer = window.setTimeout(loadIncidents, 350); });
    [elements.statusFilter, elements.severityFilter].forEach(select => select.addEventListener('change', loadIncidents));
    elements.clearFilters.addEventListener('click', () => { elements.filters.reset(); loadIncidents(); });
    window.addEventListener('speeddesk:realtime', event => {
        if (event.detail?.eventName !== 'incident-changed') return;
        window.clearTimeout(state.realtimeTimer);
        state.realtimeTimer = window.setTimeout(loadIncidents, 250);
    });
    initialize();
});
