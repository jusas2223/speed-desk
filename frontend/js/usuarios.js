import api from './api.js';

const ROLE_LABELS = Object.freeze({
    CLIENTE: 'Cliente',
    TECNICO: 'Técnico',
    GERENTE: 'Gerente'
});

const state = {
    users: [],
    organizations: [],
    loading: true
};

const elements = {};

document.addEventListener('DOMContentLoaded', () => {
    const session = api.requireAuth();
    if (!session) return;

    if (String(session.role || '').toUpperCase() !== 'GERENTE') {
        window.location.replace('dashboard.html');
        return;
    }

    cacheElements();
    bindEvents();
    loadPageData();
});

function cacheElements() {
    elements.totalBadge = document.getElementById('usersTotalBadge');
    elements.metricTotal = document.getElementById('usersMetricTotal');
    elements.metricClients = document.getElementById('usersMetricClients');
    elements.metricTeam = document.getElementById('usersMetricTeam');
    elements.metricLinked = document.getElementById('usersMetricLinked');

    elements.form = document.getElementById('userForm');
    elements.name = document.getElementById('userName');
    elements.email = document.getElementById('userEmail');
    elements.role = document.getElementById('userRole');
    elements.organizationField = document.getElementById('organizationField');
    elements.organization = document.getElementById('userOrganization');
    elements.password = document.getElementById('userPassword');
    elements.feedback = document.getElementById('userFeedback');
    elements.submit = document.getElementById('userSubmit');

    elements.filters = document.getElementById('userFilters');
    elements.query = document.getElementById('userQuery');
    elements.roleFilter = document.getElementById('userRoleFilter');
    elements.organizationFilter = document.getElementById('userOrganizationFilter');
    elements.clearFilters = document.getElementById('clearUserFilters');
    elements.summary = document.getElementById('usersSummary');
    elements.status = document.getElementById('usersStatus');
    elements.list = document.getElementById('usersList');
}

function bindEvents() {
    elements.role.addEventListener('change', updateOrganizationField);
    elements.form.addEventListener('submit', createUser);

    elements.filters.addEventListener('submit', event => event.preventDefault());
    elements.query.addEventListener('input', renderFilteredUsers);
    elements.roleFilter.addEventListener('change', renderFilteredUsers);
    elements.organizationFilter.addEventListener('change', renderFilteredUsers);
    elements.clearFilters.addEventListener('click', () => {
        elements.filters.reset();
        renderFilteredUsers();
        elements.query.focus();
    });
}

async function loadPageData() {
    state.loading = true;
    elements.list.setAttribute('aria-busy', 'true');
    setStatus('Carregando usuários...', 'loading');

    const [usersResult, organizationsResult] = await Promise.allSettled([
        api.request('/users'),
        api.request('/organizations')
    ]);

    if (usersResult.status === 'rejected') {
        state.loading = false;
        elements.list.setAttribute('aria-busy', 'false');
        setStatus(
            usersResult.reason.message || 'Não foi possível carregar os usuários.',
            'error'
        );
        return;
    }

    state.users = Array.isArray(usersResult.value) ? usersResult.value : [];

    if (organizationsResult.status === 'fulfilled') {
        state.organizations = Array.isArray(organizationsResult.value)
            ? organizationsResult.value
            : [];
        populateOrganizationOptions();
    } else {
        state.organizations = [];
        elements.organization.disabled = true;
        elements.organizationFilter.disabled = true;
    }

    state.loading = false;
    elements.list.setAttribute('aria-busy', 'false');
    setStatus('', '');
    renderMetrics();
    renderFilteredUsers();
}

function populateOrganizationOptions() {
    const activeOrganizations = state.organizations.filter(organization => organization.active);

    elements.organization.replaceChildren(createOption('', 'Sem organização'));
    activeOrganizations.forEach(organization => {
        elements.organization.appendChild(createOption(organization.id, organization.name));
    });

    elements.organizationFilter.replaceChildren(
        createOption('', 'Todas'),
        createOption('__NONE__', 'Sem organização')
    );
    state.organizations.forEach(organization => {
        elements.organizationFilter.appendChild(createOption(organization.id, organization.name));
    });

    elements.organizationFilter.disabled = false;
    updateOrganizationField();
}

function createOption(value, label) {
    const option = document.createElement('option');
    option.value = value;
    option.textContent = label;
    return option;
}

function updateOrganizationField() {
    const isClient = elements.role.value === 'CLIENTE';
    elements.organizationField.hidden = !isClient;
    elements.organization.disabled = !isClient || state.organizations.length === 0;

    if (!isClient) {
        elements.organization.value = '';
    }
}

function renderMetrics() {
    const clients = state.users.filter(user => user.role === 'CLIENTE');
    const team = state.users.filter(user => user.role === 'TECNICO' || user.role === 'GERENTE');
    const linkedClients = clients.filter(user => user.organization);

    elements.totalBadge.textContent = `${state.users.length} cadastrado${state.users.length === 1 ? '' : 's'}`;
    elements.metricTotal.textContent = String(state.users.length);
    elements.metricClients.textContent = String(clients.length);
    elements.metricTeam.textContent = String(team.length);
    elements.metricLinked.textContent = String(linkedClients.length);
}

function renderFilteredUsers() {
    if (state.loading) return;

    const query = normalize(elements.query.value);
    const role = elements.roleFilter.value;
    const organizationId = elements.organizationFilter.value;

    const filteredUsers = state.users.filter(user => {
        if (role && user.role !== role) return false;

        const currentOrganizationId = user.organization?.id || '';
        if (organizationId === '__NONE__' && currentOrganizationId) return false;
        if (organizationId && organizationId !== '__NONE__' && currentOrganizationId !== organizationId) {
            return false;
        }

        if (!query) return true;
        return [
            user.name,
            user.email,
            ROLE_LABELS[user.role],
            user.organization?.name
        ].some(value => normalize(value).includes(query));
    });

    const activeFilters = [query, role, organizationId].filter(Boolean).length;
    elements.summary.textContent = activeFilters
        ? `${filteredUsers.length} resultado(s) com ${activeFilters} filtro(s) ativo(s).`
        : `${filteredUsers.length} usuário(s) disponível(is), em ordem alfabética.`;

    elements.list.replaceChildren();
    if (filteredUsers.length === 0) {
        const empty = document.createElement('div');
        empty.className = 'users-empty-state';

        const title = document.createElement('strong');
        title.textContent = state.users.length
            ? 'Nenhum usuário corresponde aos filtros.'
            : 'Nenhum usuário cadastrado.';

        const description = document.createElement('span');
        description.textContent = state.users.length
            ? 'Ajuste a busca ou limpe os filtros para tentar novamente.'
            : 'Use o formulário para criar a primeira conta.';

        empty.append(title, description);
        elements.list.appendChild(empty);
        return;
    }

    filteredUsers.forEach(user => elements.list.appendChild(createUserRecord(user)));
}

function createUserRecord(user) {
    const record = document.createElement('article');
    record.className = 'user-record';

    const identity = document.createElement('div');
    identity.className = 'user-record-identity';

    const avatar = document.createElement('span');
    avatar.className = 'user-record-avatar';
    avatar.textContent = getInitials(user.name);

    const copy = document.createElement('span');
    copy.className = 'user-record-copy';

    const name = document.createElement('strong');
    name.textContent = user.name;

    const email = document.createElement('span');
    email.textContent = user.email;

    copy.append(name, email);
    identity.append(avatar, copy);

    const access = document.createElement('div');
    access.className = 'user-record-access';

    const role = document.createElement('span');
    role.className = `role-pill role-${String(user.role || '').toLowerCase()}`;
    role.textContent = ROLE_LABELS[user.role] || user.role;

    const organization = document.createElement('span');
    organization.className = 'user-record-organization';
    organization.textContent = user.organization?.name || (
        user.role === 'CLIENTE' ? 'Sem organização' : 'Equipe interna'
    );

    access.append(role, organization);

    const meta = document.createElement('div');
    meta.className = 'user-record-meta';

    const createdLabel = document.createElement('span');
    createdLabel.textContent = 'Criado em';

    const createdAt = document.createElement('strong');
    createdAt.textContent = formatDate(user.createdAt);

    meta.append(createdLabel, createdAt);
    record.append(identity, access, meta);
    return record;
}

async function createUser(event) {
    event.preventDefault();
    if (elements.submit.disabled) return;

    setFeedback('', '');

    const payload = {
        name: elements.name.value.trim(),
        email: elements.email.value.trim(),
        password: elements.password.value,
        role: elements.role.value,
        organizationId: elements.role.value === 'CLIENTE' && elements.organization.value
            ? elements.organization.value
            : null
    };

    const validationMessage = validatePayload(payload);
    if (validationMessage) {
        setFeedback(validationMessage, 'error');
        return;
    }

    setSubmitState(true);

    try {
        const createdUser = await api.request('/users', {
            method: 'POST',
            body: JSON.stringify(payload)
        });

        state.users = [...state.users, createdUser].sort((left, right) => (
            String(left.name).localeCompare(String(right.name), 'pt-BR', { sensitivity: 'base' })
        ));

        elements.form.reset();
        updateOrganizationField();
        renderMetrics();
        renderFilteredUsers();
        setFeedback(`Usuário ${createdUser.name} criado com sucesso.`, 'success');
        elements.name.focus();
    } catch (error) {
        const fallback = error.status === 409
            ? 'Já existe um usuário cadastrado com esse e-mail.'
            : 'Não foi possível criar o usuário.';
        setFeedback(error.message || fallback, 'error');
    } finally {
        setSubmitState(false);
    }
}

function validatePayload(payload) {
    if (!payload.name || !payload.email || !payload.password || !payload.role) {
        return 'Preencha nome, e-mail, perfil e senha.';
    }
    if (!elements.email.validity.valid) {
        return 'Informe um endereço de e-mail válido.';
    }
    if (payload.password.length < 8 || payload.password.length > 72) {
        return 'A senha deve possuir entre 8 e 72 caracteres.';
    }
    if (new TextEncoder().encode(payload.password).length > 72) {
        return 'A senha deve possuir no máximo 72 bytes em UTF-8.';
    }
    return '';
}

function setSubmitState(busy) {
    elements.submit.disabled = busy;
    elements.submit.replaceChildren();

    if (busy) {
        const spinner = document.createElement('span');
        spinner.className = 'button-spinner';
        spinner.setAttribute('aria-hidden', 'true');
        elements.submit.append(spinner, document.createTextNode(' Criando usuário...'));
        return;
    }

    const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    icon.setAttribute('class', 'icon');
    icon.setAttribute('viewBox', '0 0 24 24');
    icon.setAttribute('aria-hidden', 'true');
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', 'M12 5v14M5 12h14');
    icon.appendChild(path);
    elements.submit.append(icon, document.createTextNode(' Criar usuário'));
}

function setFeedback(message, type) {
    elements.feedback.textContent = message;
    elements.feedback.className = `feedback ${type || ''}`.trim();
    elements.feedback.hidden = !message;
}

function setStatus(message, type) {
    elements.status.textContent = message;
    elements.status.className = `list-status ${type || ''}`.trim();
    elements.status.hidden = !message;
}

function normalize(value) {
    return String(value || '')
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .toLocaleLowerCase('pt-BR')
        .trim();
}

function getInitials(name) {
    const parts = String(name || 'Usuário').trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return 'US';
    return `${parts[0][0]}${parts.length > 1 ? parts.at(-1)[0] : parts[0][1] || ''}`.toUpperCase();
}

function formatDate(value) {
    if (!value) return 'Data indisponível';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return 'Data indisponível';
    return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short' }).format(date);
}
