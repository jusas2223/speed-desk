import api from './api.js';

const TICKET_TYPE_LABELS = Object.freeze({
    GERAL: 'Geral',
    HARDWARE: 'Hardware',
    SOFTWARE: 'Software'
});

document.addEventListener('DOMContentLoaded', () => {
    const session = api.requireAuth();
    if (!session) return;

    const role = session.role ? session.role.toUpperCase() : '';
    if (role !== 'GERENTE') {
        window.location.replace('dashboard.html');
        return;
    }

    document.getElementById('welcomeMessage').textContent = (
        `Bem-vindo(a), ${session.name} | Perfil: ${role}`
    );
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

    let organizationSubmitting = false;
    let categorySubmitting = false;

    function setFeedback(element, message, type) {
        element.textContent = message;
        element.className = `feedback ${type || ''}`.trim();
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

    function formatDate(value) {
        if (!value) return 'Data indisponível';

        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return 'Data indisponível';

        return new Intl.DateTimeFormat('pt-BR', {
            dateStyle: 'short',
            timeStyle: 'short'
        }).format(date);
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
            setListStatus(
                organizationsStatus,
                error.message || 'Não foi possível carregar as organizações.',
                'error'
            );
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
            setListStatus(
                categoriesStatus,
                error.message || 'Não foi possível carregar as categorias.',
                'error'
            );
        } finally {
            categoriesList.setAttribute('aria-busy', 'false');
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
        setFeedback(organizationFeedback, '', '');
        setSubmitState(organizationSubmit, true, 'Criando...', 'Criar organização');

        try {
            await api.request('/organizations', {
                method: 'POST',
                body: JSON.stringify({ name })
            });

            organizationForm.reset();
            await loadOrganizations();
            setFeedback(organizationFeedback, 'Organização criada com sucesso.', 'success');
        } catch (error) {
            setFeedback(
                organizationFeedback,
                error.message || 'Não foi possível criar a organização.',
                'error'
            );
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
        setFeedback(categoryFeedback, '', '');
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
            const fallbackMessage = error.status === 409
                ? 'Já existe uma categoria com esse nome para o tipo selecionado.'
                : 'Não foi possível criar a categoria.';
            const errorMessage = error.status === 409
                ? (error.data && error.data.detail) || fallbackMessage
                : error.message || fallbackMessage;
            setFeedback(categoryFeedback, errorMessage, 'error');
        } finally {
            categorySubmitting = false;
            setSubmitState(categorySubmit, false, 'Criando...', 'Criar categoria');
        }
    });

    Promise.allSettled([loadOrganizations(), loadCategories()]);
});
