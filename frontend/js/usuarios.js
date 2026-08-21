import api from './api.js';

const ROLE_LABELS = Object.freeze({
    CLIENTE: 'Cliente',
    TECNICO: 'Técnico',
    GERENTE: 'Gerente'
});

const state = {
    users: [],
    organizations: [],
    loading: true,
    session: null,
    editingUser: null,
    statusUser: null,
    resetUser: null,
    lastFocusedElement: null
};

const elements = {};

document.addEventListener('DOMContentLoaded', () => {
    state.session = api.requireAuth();
    if (!state.session) return;

    if (String(state.session.role || '').toUpperCase() !== 'GERENTE') {
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
    elements.metricActive = document.getElementById('usersMetricActive');

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
    elements.statusFilter = document.getElementById('userStatusFilter');
    elements.clearFilters = document.getElementById('clearUserFilters');
    elements.summary = document.getElementById('usersSummary');
    elements.status = document.getElementById('usersStatus');
    elements.list = document.getElementById('usersList');

    elements.editModal = document.getElementById('editUserModal');
    elements.editForm = document.getElementById('editUserForm');
    elements.editName = document.getElementById('editUserName');
    elements.editEmail = document.getElementById('editUserEmail');
    elements.editRole = document.getElementById('editUserRole');
    elements.editOrganizationField = document.getElementById('editOrganizationField');
    elements.editOrganization = document.getElementById('editUserOrganization');
    elements.editFeedback = document.getElementById('editUserFeedback');
    elements.editSubmit = document.getElementById('saveEditUser');

    elements.statusModal = document.getElementById('statusUserModal');
    elements.statusTitle = document.getElementById('statusUserTitle');
    elements.statusDescription = document.getElementById('statusUserDescription');
    elements.statusFeedback = document.getElementById('statusUserFeedback');
    elements.statusConfirm = document.getElementById('confirmStatusUser');

    elements.resetModal = document.getElementById('resetUserModal');
    elements.resetDescription = document.getElementById('resetUserDescription');
    elements.resetResult = document.getElementById('passwordResetResult');
    elements.resetToken = document.getElementById('passwordResetToken');
    elements.resetLink = document.getElementById('passwordResetLink');
    elements.resetExpiration = document.getElementById('passwordResetExpiration');
    elements.resetFeedback = document.getElementById('resetUserFeedback');
    elements.resetCancel = document.getElementById('cancelResetUser');
    elements.resetConfirm = document.getElementById('confirmResetUser');
    elements.copyResetToken = document.getElementById('copyPasswordResetToken');
    elements.copyResetLink = document.getElementById('copyPasswordResetLink');
    elements.toastRegion = document.getElementById('usersToastRegion');
}

function bindEvents() {
    elements.role.addEventListener('change', updateOrganizationField);
    elements.form.addEventListener('submit', createUser);

    elements.filters.addEventListener('submit', event => event.preventDefault());
    elements.query.addEventListener('input', renderFilteredUsers);
    elements.roleFilter.addEventListener('change', renderFilteredUsers);
    elements.organizationFilter.addEventListener('change', renderFilteredUsers);
    elements.statusFilter.addEventListener('change', renderFilteredUsers);
    elements.clearFilters.addEventListener('click', () => {
        elements.filters.reset();
        renderFilteredUsers();
        elements.query.focus();
    });

    elements.list.addEventListener('click', handleUserAction);
    elements.editRole.addEventListener('change', updateEditOrganizationField);
    elements.editForm.addEventListener('submit', updateUser);
    document.getElementById('closeEditUserModal').addEventListener('click', closeEditModal);
    document.getElementById('cancelEditUser').addEventListener('click', closeEditModal);
    document.getElementById('closeStatusUserModal').addEventListener('click', closeStatusModal);
    document.getElementById('cancelStatusUser').addEventListener('click', closeStatusModal);
    elements.statusConfirm.addEventListener('click', updateUserStatus);

    document.getElementById('closeResetUserModal').addEventListener('click', closeResetModal);
    elements.resetCancel.addEventListener('click', closeResetModal);
    elements.resetConfirm.addEventListener('click', issuePasswordReset);
    elements.copyResetToken.addEventListener('click', () => copyResetValue(
        elements.resetToken,
        'Código copiado.'
    ));
    elements.copyResetLink.addEventListener('click', () => copyResetValue(
        elements.resetLink,
        'Link copiado.'
    ));

    [elements.editModal, elements.statusModal, elements.resetModal].forEach(modal => {
        modal.addEventListener('click', event => {
            if (event.target !== modal) return;
            if (modal === elements.editModal) closeEditModal();
            else if (modal === elements.statusModal) closeStatusModal();
            else closeResetModal();
        });
    });
    document.addEventListener('keydown', event => {
        if (event.key !== 'Escape') return;
        if (elements.resetModal.classList.contains('active')) closeResetModal();
        else if (elements.statusModal.classList.contains('active')) closeStatusModal();
        else if (elements.editModal.classList.contains('active')) closeEditModal();
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
    elements.editOrganization.replaceChildren(createOption('', 'Sem organização'));
    activeOrganizations.forEach(organization => {
        elements.organization.appendChild(createOption(organization.id, organization.name));
        elements.editOrganization.appendChild(createOption(organization.id, organization.name));
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
    updateEditOrganizationField();
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

function updateEditOrganizationField() {
    const isClient = elements.editRole.value === 'CLIENTE';
    elements.editOrganizationField.hidden = !isClient;
    elements.editOrganization.disabled = !isClient || state.organizations.length === 0;

    if (!isClient) {
        elements.editOrganization.value = '';
    }
}

function renderMetrics() {
    const clients = state.users.filter(user => user.role === 'CLIENTE');
    const team = state.users.filter(user => user.role === 'TECNICO' || user.role === 'GERENTE');
    const activeUsers = state.users.filter(user => user.active !== false);

    elements.totalBadge.textContent = `${state.users.length} cadastrado${state.users.length === 1 ? '' : 's'}`;
    elements.metricTotal.textContent = String(state.users.length);
    elements.metricClients.textContent = String(clients.length);
    elements.metricTeam.textContent = String(team.length);
    elements.metricActive.textContent = String(activeUsers.length);
}

function renderFilteredUsers() {
    if (state.loading) return;

    const query = normalize(elements.query.value);
    const role = elements.roleFilter.value;
    const organizationId = elements.organizationFilter.value;
    const accountStatus = elements.statusFilter.value;

    const filteredUsers = state.users.filter(user => {
        if (role && user.role !== role) return false;
        if (accountStatus === 'ACTIVE' && user.active === false) return false;
        if (accountStatus === 'INACTIVE' && user.active !== false) return false;

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
            user.organization?.name,
            user.active === false ? 'inativo' : 'ativo'
        ].some(value => normalize(value).includes(query));
    });

    const activeFilters = [query, role, organizationId, accountStatus].filter(Boolean).length;
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
    if (user.active === false) record.classList.add('is-inactive');

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

    const status = document.createElement('span');
    status.className = `status-pill ${user.active === false ? 'inactive' : 'active'}`;
    status.textContent = user.active === false ? 'Inativo' : 'Ativo';

    access.append(role, organization, status);

    const meta = document.createElement('div');
    meta.className = 'user-record-meta';

    const createdLabel = document.createElement('span');
    createdLabel.textContent = 'Criado em';

    const createdAt = document.createElement('strong');
    createdAt.textContent = formatDate(user.createdAt);

    meta.append(createdLabel, createdAt);

    const actions = document.createElement('div');
    actions.className = 'user-record-actions';

    const editButton = createActionButton('Editar', 'edit', user.id, 'btn-secondary');
    const resetButton = createActionButton(
        'Recuperar senha',
        'reset',
        user.id,
        'btn-secondary'
    );
    const isSelf = user.id === state.session.id;
    const statusButton = createActionButton(
        user.active === false ? 'Ativar' : 'Desativar',
        'status',
        user.id,
        user.active === false ? 'btn-success' : 'btn-secondary'
    );
    if (isSelf && user.active !== false) {
        statusButton.disabled = true;
        statusButton.title = 'Você não pode desativar a própria conta.';
    }

    actions.append(editButton, resetButton, statusButton);
    record.append(identity, access, meta, actions);
    return record;
}

function createActionButton(label, action, userId, tone) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `btn btn-compact ${tone}`;
    button.dataset.action = action;
    button.dataset.userId = userId;
    button.textContent = label;
    return button;
}

function handleUserAction(event) {
    const button = event.target.closest('[data-action][data-user-id]');
    if (!button || button.disabled) return;

    const user = state.users.find(item => item.id === button.dataset.userId);
    if (!user) return;

    if (button.dataset.action === 'edit') openEditModal(user, button);
    if (button.dataset.action === 'status') openStatusModal(user, button);
    if (button.dataset.action === 'reset') openResetModal(user, button);
}

function openEditModal(user, trigger) {
    state.editingUser = user;
    elements.editName.value = user.name || '';
    elements.editEmail.value = user.email || '';
    elements.editRole.value = user.role || 'CLIENTE';
    elements.editRole.disabled = user.id === state.session.id;
    updateEditOrganizationField();
    elements.editOrganization.value = user.role === 'CLIENTE'
        ? user.organization?.id || ''
        : '';
    setElementFeedback(elements.editFeedback, '', '');
    openModal(elements.editModal, elements.editName, trigger);
}

function closeEditModal() {
    if (elements.editSubmit.disabled) return;
    closeModal(elements.editModal);
    elements.editForm.reset();
    elements.editRole.disabled = false;
    state.editingUser = null;
}

function openStatusModal(user, trigger) {
    state.statusUser = user;
    const willActivate = user.active === false;
    elements.statusTitle.textContent = willActivate
        ? 'Ativar usuário'
        : 'Desativar usuário';
    elements.statusDescription.textContent = willActivate
        ? `${user.name} poderá entrar novamente e usar o sistema conforme seu perfil.`
        : `${user.name} perderá o acesso imediatamente, inclusive com tokens já emitidos.`;
    elements.statusConfirm.textContent = willActivate ? 'Ativar acesso' : 'Desativar acesso';
    elements.statusConfirm.className = `btn ${willActivate ? 'btn-success' : 'btn-danger'}`;
    setElementFeedback(elements.statusFeedback, '', '');
    openModal(elements.statusModal, elements.statusConfirm, trigger);
}

function closeStatusModal() {
    if (elements.statusConfirm.disabled) return;
    closeModal(elements.statusModal);
    state.statusUser = null;
}

function openResetModal(user, trigger) {
    state.resetUser = user;
    elements.resetDescription.textContent = `Gere um acesso temporário para ${user.name} (${user.email}). O usuário deverá receber o código ou link por um canal confiável.`;
    elements.resetToken.value = '';
    elements.resetLink.value = '';
    elements.resetExpiration.textContent = '';
    elements.resetResult.hidden = true;
    elements.resetConfirm.hidden = false;
    elements.resetCancel.textContent = 'Cancelar';
    setButtonBusy(elements.resetConfirm, false, 'Gerar código temporário');
    setElementFeedback(elements.resetFeedback, '', '');
    openModal(elements.resetModal, elements.resetConfirm, trigger);
}

function closeResetModal() {
    if (elements.resetConfirm.disabled) return;
    closeModal(elements.resetModal);
    elements.resetToken.value = '';
    elements.resetLink.value = '';
    elements.resetExpiration.textContent = '';
    elements.resetResult.hidden = true;
    elements.resetConfirm.hidden = false;
    elements.resetCancel.textContent = 'Cancelar';
    setElementFeedback(elements.resetFeedback, '', '');
    state.resetUser = null;
}

function openModal(modal, focusTarget, trigger) {
    state.lastFocusedElement = trigger || document.activeElement;
    modal.hidden = false;
    modal.removeAttribute('inert');
    modal.setAttribute('aria-hidden', 'false');
    window.requestAnimationFrame(() => {
        modal.classList.add('active');
        focusTarget.focus();
    });
}

function closeModal(modal) {
    if (modal === elements.editModal && elements.editSubmit.disabled) return;
    if (modal === elements.statusModal && elements.statusConfirm.disabled) return;
    if (modal === elements.resetModal && elements.resetConfirm.disabled) return;

    modal.classList.remove('active');
    modal.setAttribute('aria-hidden', 'true');
    modal.setAttribute('inert', '');
    modal.hidden = true;
    const focusTarget = state.lastFocusedElement;
    state.lastFocusedElement = null;
    if (focusTarget instanceof HTMLElement && document.contains(focusTarget)) {
        focusTarget.focus();
    }
}

async function issuePasswordReset() {
    const current = state.resetUser;
    if (!current || elements.resetConfirm.disabled) return;

    let completed = false;
    setButtonBusy(elements.resetConfirm, true, 'Gerando...');
    setElementFeedback(elements.resetFeedback, '', '');
    try {
        const issued = await api.request(`/users/${current.id}/password-reset`, {
            method: 'POST'
        });
        if (!issued?.token || !issued?.expiresAt) {
            throw new Error('O servidor não retornou um código de recuperação válido.');
        }
        const resetUrl = new URL('redefinir-senha.html', window.location.href);
        resetUrl.searchParams.set('token', issued.token);

        elements.resetToken.value = issued.token;
        elements.resetLink.value = resetUrl.toString();
        elements.resetExpiration.textContent = `Válido até ${formatDateTime(issued.expiresAt)}.`;
        elements.resetResult.hidden = false;
        elements.resetConfirm.hidden = true;
        elements.resetCancel.textContent = 'Fechar';
        setButtonBusy(elements.resetConfirm, false, 'Gerar código temporário');
        setElementFeedback(
            elements.resetFeedback,
            `Recuperação gerada para ${issued.userName || current.name}. Copie agora: o código não será exibido novamente depois que esta janela for fechada.`,
            'success'
        );
        completed = true;
        elements.copyResetLink.focus();
    } catch (error) {
        setElementFeedback(
            elements.resetFeedback,
            error.message || 'Não foi possível gerar a recuperação de senha.',
            'error'
        );
    } finally {
        if (!completed) {
            setButtonBusy(elements.resetConfirm, false, 'Gerar código temporário');
        }
    }
}

async function copyResetValue(field, successMessage) {
    const value = field.value;
    if (!value) return;

    try {
        if (navigator.clipboard?.writeText) {
            await navigator.clipboard.writeText(value);
        } else {
            field.focus();
            field.select();
            const copied = document.execCommand('copy');
            field.setSelectionRange(0, 0);
            if (!copied) throw new Error('copy-unavailable');
        }
        setElementFeedback(elements.resetFeedback, successMessage, 'success');
    } catch {
        field.focus();
        field.select();
        setElementFeedback(
            elements.resetFeedback,
            'A cópia automática não foi permitida. O conteúdo foi selecionado para cópia manual.',
            'error'
        );
    }
}

async function updateUser(event) {
    event.preventDefault();
    const current = state.editingUser;
    if (!current || elements.editSubmit.disabled) return;

    const payload = {
        name: elements.editName.value.trim(),
        email: elements.editEmail.value.trim(),
        role: elements.editRole.value,
        organizationId: elements.editRole.value === 'CLIENTE'
            && elements.editOrganization.value
            ? elements.editOrganization.value
            : null
    };
    if (!payload.name || !payload.email || !payload.role) {
        setElementFeedback(
            elements.editFeedback,
            'Preencha nome, e-mail e perfil.',
            'error'
        );
        return;
    }
    if (!elements.editEmail.validity.valid) {
        setElementFeedback(elements.editFeedback, 'Informe um e-mail válido.', 'error');
        return;
    }

    setButtonBusy(elements.editSubmit, true, 'Salvando...');
    try {
        const updated = await api.request(`/users/${current.id}`, {
            method: 'PUT',
            body: JSON.stringify(payload)
        });
        replaceUser(updated);
        if (updated.id === state.session.id) {
            state.session = api.updateSessionProfile(updated);
        }
        setButtonBusy(elements.editSubmit, false, 'Salvar alterações');
        closeEditModal();
        showToast(`Dados de ${updated.name} atualizados.`, 'success');
    } catch (error) {
        setElementFeedback(
            elements.editFeedback,
            error.message || 'Não foi possível atualizar o usuário.',
            'error'
        );
    } finally {
        setButtonBusy(elements.editSubmit, false, 'Salvar alterações');
    }
}

async function updateUserStatus() {
    const current = state.statusUser;
    if (!current || elements.statusConfirm.disabled) return;

    const active = current.active === false;
    setButtonBusy(
        elements.statusConfirm,
        true,
        active ? 'Ativando...' : 'Desativando...'
    );
    try {
        const updated = await api.request(`/users/${current.id}/status`, {
            method: 'PATCH',
            body: JSON.stringify({ active })
        });
        replaceUser(updated);
        setButtonBusy(
            elements.statusConfirm,
            false,
            active ? 'Ativar acesso' : 'Desativar acesso'
        );
        closeStatusModal();
        showToast(
            `${updated.name} foi ${updated.active ? 'ativado' : 'desativado'}.`,
            'success'
        );
    } catch (error) {
        setElementFeedback(
            elements.statusFeedback,
            error.message || 'Não foi possível alterar o acesso.',
            'error'
        );
    } finally {
        setButtonBusy(
            elements.statusConfirm,
            false,
            active ? 'Ativar acesso' : 'Desativar acesso'
        );
    }
}

function replaceUser(updated) {
    state.users = state.users
        .map(user => user.id === updated.id ? updated : user)
        .sort((left, right) => String(left.name).localeCompare(
            String(right.name),
            'pt-BR',
            { sensitivity: 'base' }
        ));
    renderMetrics();
    renderFilteredUsers();
}

function setButtonBusy(button, busy, label) {
    button.disabled = busy;
    button.replaceChildren();
    if (busy) {
        const spinner = document.createElement('span');
        spinner.className = 'button-spinner';
        spinner.setAttribute('aria-hidden', 'true');
        button.append(spinner, document.createTextNode(` ${label}`));
        return;
    }
    button.textContent = label;
}

function setElementFeedback(element, message, type) {
    element.textContent = message;
    element.className = `feedback ${type || ''}`.trim();
    element.hidden = !message;
}

function showToast(message, tone = '') {
    const toast = document.createElement('div');
    toast.className = `toast ${tone}`.trim();
    toast.textContent = message;
    elements.toastRegion.appendChild(toast);
    window.setTimeout(() => toast.remove(), 3600);
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

function formatDateTime(value) {
    if (!value) return 'horário indisponível';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return 'horário indisponível';
    return new Intl.DateTimeFormat('pt-BR', {
        dateStyle: 'short',
        timeStyle: 'short'
    }).format(date);
}
