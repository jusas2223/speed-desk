import api from './api.js';

const TICKET_TYPE_LABELS = Object.freeze({
    GERAL: 'Geral',
    HARDWARE: 'Hardware',
    SOFTWARE: 'Software'
});

const PRIORITY_LABELS = Object.freeze({
    BAIXA: 'Baixa',
    NORMAL: 'Normal',
    ALTA: 'Alta',
    CRITICA: 'Crítica'
});

const PRIORITY_ORDER = Object.freeze(['BAIXA', 'NORMAL', 'ALTA', 'CRITICA']);

document.addEventListener('DOMContentLoaded', () => {
    const session = api.requireAuth();
    if (!session) return;

    const role = String(session.role || '').toUpperCase();
    if (role !== 'GERENTE') {
        window.location.replace('dashboard.html');
        return;
    }

    document.getElementById('welcomeMessage').textContent = `Bem-vindo(a), ${session.name} | Perfil: ${role}`;

    const organizationForm = document.getElementById('organizationForm');
    const organizationName = document.getElementById('organizationName');
    const organizationSubmit = document.getElementById('organizationSubmit');
    const organizationFeedback = document.getElementById('organizationFeedback');
    const organizationsStatus = document.getElementById('organizationsStatus');
    const organizationsList = document.getElementById('organizationsList');
    const categoryForm = document.getElementById('categoryForm');
    const categoryName = document.getElementById('categoryName');
    const categoryTicketType = document.getElementById('categoryTicketType');
    const categorySubmit = document.getElementById('categorySubmit');
    const categoryFeedback = document.getElementById('categoryFeedback');
    const categoriesStatus = document.getElementById('categoriesStatus');
    const categoriesList = document.getElementById('categoriesList');
    const slaForm = document.getElementById('slaPoliciesForm');
    const slaFeedback = document.getElementById('slaPoliciesFeedback');
    const slaStatus = document.getElementById('slaPoliciesStatus');
    const toastRegion = document.getElementById('settingsToastRegion');

    let organizationSubmitting = false;
    let categorySubmitting = false;
    let slaSubmitting = false;

    function setFeedback(element, message, type = '') {
        element.textContent = message;
        element.className = `feedback ${type}`.trim();
        element.hidden = !message;
    }

    function setListStatus(element, message, type = '') {
        element.textContent = message;
        element.className = `list-status ${type}`.trim();
        element.hidden = !message;
    }

    function setSubmitState(button, isBusy, busyLabel, defaultLabel) {
        button.disabled = isBusy;
        button.textContent = isBusy ? busyLabel : defaultLabel;
    }

    function showToast(message, tone = '') {
        const toast = document.createElement('div');
        toast.className = `toast ${tone}`.trim();
        toast.textContent = message;
        toastRegion.appendChild(toast);
        window.setTimeout(() => toast.remove(), 3600);
    }

    function formatDate(value) {
        if (!value) return 'Data indisponível';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return 'Data indisponível';
        return new Intl.DateTimeFormat('pt-BR', {
            dateStyle: 'short',
            timeStyle: 'short'
        }).format(date);
    }

    function formatMinutes(minutes) {
        const value = Number(minutes) || 0;
        if (value < 60) return `${value} min`;
        const hours = Math.floor(value / 60);
        const remainder = value % 60;
        if (hours < 24) return remainder ? `${hours}h ${remainder}min` : `${hours}h`;
        const days = Math.floor(hours / 24);
        const remainingHours = hours % 24;
        return remainingHours ? `${days}d ${remainingHours}h` : `${days}d`;
    }

    function createEmptyState(message) {
        const emptyState = document.createElement('p');
        emptyState.className = 'empty-state';
        emptyState.textContent = message;
        return emptyState;
    }

    function createStatusPill(active) {
        const status = document.createElement('span');
        status.className = `status-pill ${active ? 'active' : 'inactive'}`;
        status.textContent = active ? 'Ativa' : 'Inativa';
        return status;
    }

    function createRecordMeta(createdAt) {
        const meta = document.createElement('div');
        meta.className = 'record-meta';
        const date = document.createElement('span');
        date.textContent = `Criada em ${formatDate(createdAt)}`;
        meta.appendChild(date);
        return meta;
    }

    function renderOrganizations(organizations) {
        organizationsList.replaceChildren();
        if (!organizations.length) {
            organizationsList.appendChild(createEmptyState('Nenhuma organização cadastrada.'));
            return;
        }
        organizations.forEach(organization => {
            const item = document.createElement('article');
            item.className = 'config-item';
            const header = document.createElement('div');
            header.className = 'record-header';
            const name = document.createElement('h3');
            name.className = 'record-name';
            name.textContent = organization.name;
            header.append(name, createStatusPill(organization.active));
            item.append(header, createRecordMeta(organization.createdAt));
            organizationsList.appendChild(item);
        });
    }

    function renderCategories(categories) {
        categoriesList.replaceChildren();
        if (!categories.length) {
            categoriesList.appendChild(createEmptyState('Nenhuma categoria ativa cadastrada.'));
            return;
        }
        categories.forEach(category => {
            const item = document.createElement('article');
            item.className = 'config-item';
            const header = document.createElement('div');
            header.className = 'record-header';
            const name = document.createElement('h3');
            name.className = 'record-name';
            name.textContent = category.name;
            header.append(name, createStatusPill(category.active));
            const meta = createRecordMeta(category.createdAt);
            const type = document.createElement('span');
            type.className = 'type-pill';
            type.textContent = TICKET_TYPE_LABELS[category.ticketType] || category.ticketType;
            meta.prepend(type);
            item.append(header, meta);
            categoriesList.appendChild(item);
        });
    }

    function createNumberField({ id, name, label, value, describedBy, min, max }) {
        const group = document.createElement('div');
        group.className = 'form-group';
        const fieldLabel = document.createElement('label');
        fieldLabel.className = 'form-label';
        fieldLabel.htmlFor = id;
        fieldLabel.textContent = label;
        const input = document.createElement('input');
        input.id = id;
        input.className = 'form-control';
        input.type = 'number';
        input.name = name;
        input.min = String(min);
        input.max = String(max);
        input.step = '1';
        input.required = true;
        input.value = String(value);
        input.setAttribute('aria-describedby', describedBy);
        group.append(fieldLabel, input);
        return group;
    }

    function renderSlaPolicies(policies) {
        const byPriority = new Map(policies.map(policy => [policy.priority, policy]));
        const fragment = document.createDocumentFragment();
        PRIORITY_ORDER.forEach(priority => {
            const policy = byPriority.get(priority);
            if (!policy) return;
            const card = document.createElement('fieldset');
            card.className = `sla-policy-card priority-${priority.toLowerCase()}`;
            card.dataset.priority = priority;
            const legend = document.createElement('legend');
            legend.textContent = PRIORITY_LABELS[priority];
            const summaryId = `slaSummary${priority}`;
            const summary = document.createElement('p');
            summary.id = summaryId;
            summary.className = 'sla-policy-summary';
            summary.textContent = `${formatMinutes(policy.durationMinutes)} de prazo · alerta nos últimos ${formatMinutes(policy.warningMinutes)}`;
            const fields = document.createElement('div');
            fields.className = 'sla-policy-fields';
            fields.append(
                createNumberField({
                    id: `slaDuration${priority}`,
                    name: `${priority}.durationMinutes`,
                    label: 'Prazo total (min)',
                    value: policy.durationMinutes,
                    describedBy: summaryId,
                    min: 1,
                    max: 43200
                }),
                createNumberField({
                    id: `slaWarning${priority}`,
                    name: `${priority}.warningMinutes`,
                    label: 'Janela de alerta (min)',
                    value: policy.warningMinutes,
                    describedBy: summaryId,
                    min: 0,
                    max: 10080
                })
            );
            card.append(legend, summary, fields);
            fragment.appendChild(card);
        });

        if (!fragment.childNodes.length) {
            setListStatus(slaStatus, 'Nenhuma política de SLA foi devolvida pela API.', 'error');
            slaForm.hidden = true;
            return;
        }
        const actions = document.createElement('div');
        actions.className = 'sla-policy-actions';
        const hint = document.createElement('p');
        hint.textContent = 'A janela de alerta deve ser menor que o prazo total.';
        const submit = document.createElement('button');
        submit.id = 'saveSlaPolicies';
        submit.className = 'btn btn-primary';
        submit.type = 'submit';
        submit.textContent = 'Salvar políticas de SLA';
        actions.append(hint, submit);
        slaForm.replaceChildren(fragment, actions);
        slaForm.hidden = false;
        slaForm.setAttribute('aria-busy', 'false');
        setListStatus(slaStatus, '');
    }

    async function loadOrganizations() {
        organizationsList.replaceChildren();
        organizationsList.setAttribute('aria-busy', 'true');
        setListStatus(organizationsStatus, 'Carregando organizações...', 'loading');
        try {
            const organizations = await api.request('/organizations');
            renderOrganizations(Array.isArray(organizations) ? organizations : []);
            setListStatus(organizationsStatus, '');
        } catch (error) {
            console.error('Erro ao carregar organizações:', error);
            setListStatus(organizationsStatus, error.message || 'Não foi possível carregar as organizações.', 'error');
        } finally {
            organizationsList.setAttribute('aria-busy', 'false');
        }
    }

    async function loadCategories() {
        categoriesList.replaceChildren();
        categoriesList.setAttribute('aria-busy', 'true');
        setListStatus(categoriesStatus, 'Carregando categorias...', 'loading');
        try {
            const categories = await api.request('/ticket-categories');
            renderCategories(Array.isArray(categories) ? categories : []);
            setListStatus(categoriesStatus, '');
        } catch (error) {
            console.error('Erro ao carregar categorias:', error);
            setListStatus(categoriesStatus, error.message || 'Não foi possível carregar as categorias.', 'error');
        } finally {
            categoriesList.setAttribute('aria-busy', 'false');
        }
    }

    async function loadSlaPolicies() {
        slaForm.hidden = true;
        slaForm.setAttribute('aria-busy', 'true');
        setListStatus(slaStatus, 'Carregando políticas de SLA...', 'loading');
        setFeedback(slaFeedback, '');
        try {
            const policies = await api.request('/sla-policies');
            renderSlaPolicies(Array.isArray(policies) ? policies : []);
        } catch (error) {
            console.error('Erro ao carregar políticas de SLA:', error);
            setListStatus(slaStatus, error.message || 'Não foi possível carregar as políticas de SLA.', 'error');
        } finally {
            slaForm.setAttribute('aria-busy', 'false');
        }
    }

    organizationForm.addEventListener('submit', async event => {
        event.preventDefault();
        if (organizationSubmitting) return;
        const name = organizationName.value.trim();
        if (!name) {
            setFeedback(organizationFeedback, 'Informe o nome da organização.', 'error');
            organizationName.focus();
            return;
        }
        organizationSubmitting = true;
        setFeedback(organizationFeedback, '');
        setSubmitState(organizationSubmit, true, 'Criando...', 'Criar organização');
        try {
            await api.request('/organizations', { method: 'POST', body: JSON.stringify({ name }) });
            organizationForm.reset();
            await loadOrganizations();
            setFeedback(organizationFeedback, 'Organização criada com sucesso.', 'success');
        } catch (error) {
            setFeedback(organizationFeedback, error.message || 'Não foi possível criar a organização.', 'error');
        } finally {
            organizationSubmitting = false;
            setSubmitState(organizationSubmit, false, 'Criando...', 'Criar organização');
        }
    });

    categoryForm.addEventListener('submit', async event => {
        event.preventDefault();
        if (categorySubmitting) return;
        const name = categoryName.value.trim();
        const ticketType = categoryTicketType.value;
        if (!name || !ticketType) {
            setFeedback(categoryFeedback, 'Informe o nome e o tipo da categoria.', 'error');
            (!name ? categoryName : categoryTicketType).focus();
            return;
        }
        categorySubmitting = true;
        setFeedback(categoryFeedback, '');
        setSubmitState(categorySubmit, true, 'Criando...', 'Criar categoria');
        try {
            await api.request('/ticket-categories', {
                method: 'POST',
                body: JSON.stringify({ name, ticketType })
            });
            categoryForm.reset();
            await loadCategories();
            setFeedback(categoryFeedback, 'Categoria criada com sucesso.', 'success');
        } catch (error) {
            const fallback = error.status === 409
                ? 'Já existe uma categoria com esse nome para o tipo selecionado.'
                : 'Não foi possível criar a categoria.';
            setFeedback(categoryFeedback, error.message || fallback, 'error');
        } finally {
            categorySubmitting = false;
            setSubmitState(categorySubmit, false, 'Criando...', 'Criar categoria');
        }
    });

    slaForm.addEventListener('input', event => {
        const fieldset = event.target.closest('[data-priority]');
        if (!fieldset) return;
        const priority = fieldset.dataset.priority;
        const duration = Number(document.getElementById(`slaDuration${priority}`).value);
        const warning = Number(document.getElementById(`slaWarning${priority}`).value);
        const summary = document.getElementById(`slaSummary${priority}`);
        if (Number.isInteger(duration) && Number.isInteger(warning) && duration > 0 && warning >= 0) {
            summary.textContent = `${formatMinutes(duration)} de prazo · alerta nos últimos ${formatMinutes(warning)}`;
        }
    });

    slaForm.addEventListener('submit', async event => {
        event.preventDefault();
        if (slaSubmitting) return;
        const policies = [];
        for (const priority of PRIORITY_ORDER) {
            const durationField = document.getElementById(`slaDuration${priority}`);
            const warningField = document.getElementById(`slaWarning${priority}`);
            if (!durationField || !warningField) continue;
            const durationMinutes = Number(durationField.value);
            const warningMinutes = Number(warningField.value);
            if (!Number.isInteger(durationMinutes) || durationMinutes < 1 || durationMinutes > 43200) {
                setFeedback(slaFeedback, `Informe um prazo inteiro positivo para a prioridade ${PRIORITY_LABELS[priority]}.`, 'error');
                durationField.focus();
                return;
            }
            if (!Number.isInteger(warningMinutes) || warningMinutes < 0 || warningMinutes > 10080 || warningMinutes >= durationMinutes) {
                setFeedback(slaFeedback, `A janela de alerta de ${PRIORITY_LABELS[priority]} deve ser não negativa e menor que o prazo total.`, 'error');
                warningField.focus();
                return;
            }
            policies.push({ priority, durationMinutes, warningMinutes });
        }
        if (!policies.length) return;

        slaSubmitting = true;
        setFeedback(slaFeedback, '');
        const submit = document.getElementById('saveSlaPolicies');
        setSubmitState(submit, true, 'Salvando...', 'Salvar políticas de SLA');
        slaForm.querySelectorAll('input').forEach(input => { input.disabled = true; });
        try {
            await Promise.all(policies.map(policy => api.request(`/sla-policies/${policy.priority}`, {
                method: 'PUT',
                body: JSON.stringify({
                    durationMinutes: policy.durationMinutes,
                    warningMinutes: policy.warningMinutes
                })
            })));
            await loadSlaPolicies();
            setFeedback(slaFeedback, 'Políticas atualizadas com sucesso.', 'success');
            showToast('Configuração de SLA salva.', 'success');
        } catch (error) {
            setFeedback(slaFeedback, error.message || 'Não foi possível salvar todas as políticas.', 'error');
        } finally {
            slaSubmitting = false;
            const currentSubmit = document.getElementById('saveSlaPolicies');
            if (currentSubmit) setSubmitState(currentSubmit, false, 'Salvando...', 'Salvar políticas de SLA');
            slaForm.querySelectorAll('input').forEach(input => { input.disabled = false; });
        }
    });

    Promise.allSettled([loadSlaPolicies(), loadOrganizations(), loadCategories()]);
});
