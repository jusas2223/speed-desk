import api from './api.js';

const ASSET_TYPE_LABELS = Object.freeze({
    NOTEBOOK: 'Notebook',
    DESKTOP: 'Desktop',
    MONITOR: 'Monitor',
    IMPRESSORA: 'Impressora',
    SERVIDOR: 'Servidor',
    EQUIPAMENTO_REDE: 'Equipamento de rede',
    PERIFERICO: 'Periférico',
    OUTRO: 'Outro'
});

const ASSET_STATUS_LABELS = Object.freeze({
    ATIVO: 'Ativo',
    EM_MANUTENCAO: 'Em manutenção',
    INATIVO: 'Inativo',
    DESCARTADO: 'Descartado'
});

const WARRANTY_LABELS = Object.freeze({
    VIGENTE: 'Vigente',
    EXPIRA_EM_BREVE: 'Expira em breve',
    EXPIRADA: 'Expirada',
    NAO_INFORMADA: 'Não informada',
    NAO_ELEGIVEL: 'Não elegível'
});

const TYPE_TONES = Object.freeze({
    NOTEBOOK: 'brand',
    DESKTOP: 'brand',
    MONITOR: 'info',
    IMPRESSORA: 'warning',
    SERVIDOR: 'danger',
    EQUIPAMENTO_REDE: 'cyan',
    PERIFERICO: 'neutral',
    OUTRO: 'neutral'
});

document.addEventListener('DOMContentLoaded', async () => {
    const session = api.requireAuth();
    if (!session) return;

    const role = String(session.role || '').toUpperCase();
    const canEdit = role === 'CLIENTE' || role === 'GERENTE';
    const isManager = role === 'GERENTE';
    const clientsById = new Map();
    clientsById.set(session.id, {
        id: session.id,
        name: session.name,
        email: session.email,
        active: true
    });

    const elements = {
        title: document.getElementById('assetsPageTitle'),
        description: document.getElementById('assetsPageDescription'),
        count: document.getElementById('assetCount'),
        query: document.getElementById('assetQuery'),
        filters: document.getElementById('assetFilters'),
        typeFilter: document.getElementById('assetTypeFilter'),
        statusFilter: document.getElementById('assetStatusFilter'),
        warrantyFilter: document.getElementById('assetWarrantyFilter'),
        clientFilterGroup: document.getElementById('assetClientFilterGroup'),
        clientFilter: document.getElementById('assetClientFilter'),
        clearFilters: document.getElementById('clearFiltersBtn'),
        resultsSummary: document.getElementById('assetResultsSummary'),
        tableBody: document.getElementById('assetsTableBody'),
        summaryTotal: document.getElementById('summaryTotal'),
        summaryActive: document.getElementById('summaryActive'),
        summaryMaintenance: document.getElementById('summaryMaintenance'),
        summaryWarranty: document.getElementById('summaryWarranty'),
        openModalButton: document.getElementById('btnOpenModal'),
        modal: document.getElementById('assetModal'),
        modalTitle: document.getElementById('assetModalTitle'),
        modalDescription: document.getElementById('assetModalDescription'),
        closeModalButton: document.getElementById('btnCloseModal'),
        cancelModalButton: document.getElementById('btnCancelModal'),
        form: document.getElementById('assetForm'),
        model: document.getElementById('assetModel'),
        manufacturer: document.getElementById('assetManufacturer'),
        type: document.getElementById('assetType'),
        serial: document.getElementById('assetSerial'),
        status: document.getElementById('assetStatus'),
        ownerGroup: document.getElementById('assetOwnerGroup'),
        owner: document.getElementById('assetOwner'),
        ownerHelper: document.getElementById('assetOwnerHelper'),
        purchaseDate: document.getElementById('assetPurchaseDate'),
        warrantyEndDate: document.getElementById('assetWarrantyEndDate'),
        warrantyProvider: document.getElementById('assetWarrantyProvider'),
        formError: document.getElementById('assetFormError'),
        submit: document.getElementById('btnSubmitAsset'),
        toastRegion: document.getElementById('toastRegion')
    };

    let assets = [];
    let editingAsset = null;
    let lastFocusedElement = null;
    let requestSequence = 0;
    let searchTimer = null;

    configurePageForRole();
    restoreFiltersFromUrl();
    if (isManager) await loadClients();
    await loadAssets();

    function configurePageForRole() {
        if (role === 'CLIENTE') {
            elements.title.textContent = 'Meus equipamentos';
            elements.description.textContent = 'Acompanhe os equipamentos vinculados à sua conta, garantias e atendimentos.';
        } else if (role === 'TECNICO') {
            elements.title.textContent = 'Consulta de ativos';
            elements.description.textContent = 'Consulte o inventário e o histórico técnico antes de iniciar um atendimento.';
        }

        elements.openModalButton.hidden = !canEdit;
        elements.clientFilterGroup.hidden = !isManager;
        elements.clientFilter.disabled = !isManager;
        elements.ownerGroup.hidden = !isManager;
        elements.owner.disabled = !isManager;
    }

    async function loadClients() {
        try {
            const response = await api.request('/users');
            const clients = (Array.isArray(response) ? response : [])
                .filter(user => String(user.role).toUpperCase() === 'CLIENTE')
                .sort((a, b) => String(a.name).localeCompare(String(b.name), 'pt-BR'));

            clients.forEach(client => clientsById.set(client.id, client));
            populateClientSelect(elements.clientFilter, clients, true, false);
            populateClientSelect(elements.owner, clients, false, true);

            const clientFromUrl = new URLSearchParams(window.location.search).get('clienteId');
            if (clientFromUrl && clientsById.has(clientFromUrl)) {
                elements.clientFilter.value = clientFromUrl;
            }
        } catch (error) {
            elements.clientFilter.disabled = true;
            elements.owner.disabled = true;
            showToast(error.message || 'Não foi possível carregar os clientes.', 'error');
        }
    }

    function populateClientSelect(select, clients, includeAll, activeOnly) {
        const firstOption = document.createElement('option');
        firstOption.value = '';
        firstOption.textContent = includeAll ? 'Todos os clientes' : 'Selecione o cliente';
        const options = [firstOption];

        clients
            .filter(client => !activeOnly || client.active !== false)
            .forEach(client => {
                const option = document.createElement('option');
                option.value = client.id;
                option.textContent = `${client.name}${client.active === false ? ' (inativo)' : ''}`;
                options.push(option);
            });
        select.replaceChildren(...options);
        select.disabled = false;
    }

    function restoreFiltersFromUrl() {
        const params = new URLSearchParams(window.location.search);
        elements.query.value = params.get('query') || '';
        elements.typeFilter.value = params.get('tipo') || '';
        elements.statusFilter.value = params.get('status') || '';
        elements.warrantyFilter.value = params.get('warrantyState') || '';
    }

    function currentFilters() {
        return {
            query: elements.query.value.trim(),
            tipo: elements.typeFilter.value,
            status: elements.statusFilter.value,
            warrantyState: elements.warrantyFilter.value,
            clienteId: isManager ? elements.clientFilter.value : ''
        };
    }

    function queryString(filters, includeSearch = true) {
        const params = new URLSearchParams();
        Object.entries(filters).forEach(([key, value]) => {
            if (value && (includeSearch || key !== 'query')) params.set(key, value);
        });
        return params.toString();
    }

    function syncFiltersToUrl(filters) {
        const query = queryString(filters);
        const nextUrl = `${window.location.pathname}${query ? `?${query}` : ''}`;
        window.history.replaceState({}, '', nextUrl);
    }

    async function loadAssets() {
        const sequence = ++requestSequence;
        const filters = currentFilters();
        syncFiltersToUrl(filters);
        setTableState('Carregando ativos...');
        elements.resultsSummary.textContent = 'Consultando o inventário...';

        const params = queryString(filters);
        const alertParams = new URLSearchParams();
        if (filters.clienteId) alertParams.set('clienteId', filters.clienteId);

        try {
            const [assetResponse, alertResponse] = await Promise.all([
                api.request(`/assets${params ? `?${params}` : ''}`),
                api.request(`/assets/warranty-alerts${alertParams.size ? `?${alertParams}` : ''}`)
            ]);
            if (sequence !== requestSequence) return;

            assets = Array.isArray(assetResponse) ? assetResponse : [];
            const alerts = Array.isArray(alertResponse) ? alertResponse : [];
            renderAssets(assets);
            renderSummary(assets, alerts);
        } catch (error) {
            if (sequence !== requestSequence) return;
            assets = [];
            renderLoadError(error.message || 'Não foi possível carregar os ativos.');
        }
    }

    function renderAssets(items) {
        elements.tableBody.replaceChildren();
        elements.tableBody.setAttribute('aria-busy', 'false');
        elements.count.textContent = `${items.length} ${items.length === 1 ? 'ativo' : 'ativos'}`;
        elements.resultsSummary.textContent = items.length === 0
            ? 'Nenhum ativo corresponde aos filtros aplicados.'
            : `${items.length} ${items.length === 1 ? 'equipamento encontrado' : 'equipamentos encontrados'}.`;

        if (items.length === 0) {
            setTableState('Nenhum ativo encontrado. Ajuste os filtros ou cadastre um novo equipamento.', false, false);
            return;
        }

        const fragment = document.createDocumentFragment();
        items.forEach(asset => fragment.appendChild(createAssetRow(asset)));
        elements.tableBody.appendChild(fragment);
    }

    function createAssetRow(asset) {
        const row = document.createElement('tr');
        row.className = 'asset-catalog-row';
        if (asset.warrantyState === 'EXPIRA_EM_BREVE') row.classList.add('has-warranty-alert');

        const primaryCell = document.createElement('td');
        primaryCell.dataset.label = 'Equipamento';
        const primary = document.createElement('div');
        primary.className = 'asset-primary';
        const icon = document.createElement('span');
        icon.className = `asset-type-icon tone-${TYPE_TONES[asset.tipo] || 'neutral'}`;
        icon.setAttribute('aria-hidden', 'true');
        icon.textContent = assetTypeGlyph(asset.tipo);
        const primaryCopy = document.createElement('span');
        primaryCopy.className = 'asset-primary-copy';
        const modelLink = document.createElement('a');
        modelLink.className = 'asset-model-link';
        modelLink.href = `ativo.html?id=${encodeURIComponent(asset.id)}`;
        modelLink.textContent = assetModel(asset);
        const manufacturer = document.createElement('span');
        manufacturer.className = 'asset-manufacturer';
        manufacturer.textContent = asset.fabricante || 'Fabricante não informado';
        primaryCopy.append(modelLink, manufacturer);
        primary.append(icon, primaryCopy);
        primaryCell.appendChild(primary);

        const ownerCell = document.createElement('td');
        ownerCell.dataset.label = 'Proprietário';
        const owner = ownerFor(asset.clienteId);
        const ownerName = document.createElement('span');
        ownerName.className = 'asset-cell-primary';
        ownerName.textContent = owner.name;
        const ownerMeta = document.createElement('span');
        ownerMeta.className = 'asset-cell-secondary';
        ownerMeta.textContent = owner.meta;
        ownerCell.append(ownerName, ownerMeta);

        const typeCell = document.createElement('td');
        typeCell.dataset.label = 'Tipo';
        typeCell.appendChild(createBadge(ASSET_TYPE_LABELS[asset.tipo] || humanize(asset.tipo), `asset-type-badge tone-${TYPE_TONES[asset.tipo] || 'neutral'}`));

        const statusCell = document.createElement('td');
        statusCell.dataset.label = 'Situação';
        statusCell.appendChild(createBadge(ASSET_STATUS_LABELS[asset.status] || humanize(asset.status), `asset-status-badge status-${String(asset.status).toLowerCase()}`));

        const warrantyCell = document.createElement('td');
        warrantyCell.dataset.label = 'Garantia';
        warrantyCell.appendChild(createWarrantyPresentation(asset));

        const serialCell = document.createElement('td');
        serialCell.dataset.label = 'Serial';
        const serial = document.createElement('code');
        serial.className = 'asset-serial';
        serial.textContent = assetSerial(asset) || '—';
        serialCell.appendChild(serial);

        const actionsCell = document.createElement('td');
        actionsCell.dataset.label = 'Ações';
        const actions = document.createElement('div');
        actions.className = 'asset-row-actions';
        const detailsLink = document.createElement('a');
        detailsLink.className = 'btn btn-secondary btn-compact';
        detailsLink.href = `ativo.html?id=${encodeURIComponent(asset.id)}`;
        detailsLink.textContent = 'Detalhes';
        actions.appendChild(detailsLink);
        if (canEdit) {
            const editButton = document.createElement('button');
            editButton.className = 'asset-icon-action';
            editButton.type = 'button';
            editButton.title = `Editar ${assetModel(asset)}`;
            editButton.setAttribute('aria-label', `Editar ${assetModel(asset)}`);
            editButton.textContent = '✎';
            editButton.addEventListener('click', () => openModal(asset));
            actions.appendChild(editButton);
        }
        actionsCell.appendChild(actions);

        row.append(primaryCell, ownerCell, typeCell, statusCell, warrantyCell, serialCell, actionsCell);
        return row;
    }

    function renderSummary(items, alerts) {
        elements.summaryTotal.textContent = String(items.length);
        elements.summaryActive.textContent = String(items.filter(asset => asset.status === 'ATIVO').length);
        elements.summaryMaintenance.textContent = String(items.filter(asset => asset.status === 'EM_MANUTENCAO').length);
        elements.summaryWarranty.textContent = String(alerts.length);
    }

    function renderLoadError(message) {
        elements.count.textContent = 'Indisponível';
        elements.resultsSummary.textContent = message;
        elements.summaryTotal.textContent = '—';
        elements.summaryActive.textContent = '—';
        elements.summaryMaintenance.textContent = '—';
        elements.summaryWarranty.textContent = '—';
        setTableState(message, true);
    }

    function setTableState(message, error = false, busy = true) {
        const row = document.createElement('tr');
        row.className = `asset-state-row${error ? ' is-error' : ''}`;
        const cell = document.createElement('td');
        cell.colSpan = 7;
        const state = document.createElement('div');
        state.className = 'asset-table-state';
        state.textContent = message;
        cell.appendChild(state);
        row.appendChild(cell);
        elements.tableBody.replaceChildren(row);
        elements.tableBody.setAttribute('aria-busy', String(busy));
    }

    function ownerFor(clientId) {
        if (clientId === session.id) {
            return { name: session.name, meta: role === 'CLIENTE' ? 'Você' : session.email };
        }
        const client = clientsById.get(clientId);
        if (client) return { name: client.name, meta: client.email || 'Cliente cadastrado' };
        return { name: 'Cliente vinculado', meta: compactId(clientId) };
    }

    function createBadge(label, className) {
        const badge = document.createElement('span');
        badge.className = className;
        badge.textContent = label;
        return badge;
    }

    function createWarrantyPresentation(asset) {
        const wrapper = document.createElement('span');
        wrapper.className = 'asset-warranty';
        const badge = createBadge(
            WARRANTY_LABELS[asset.warrantyState] || humanize(asset.warrantyState),
            `asset-warranty-badge warranty-${String(asset.warrantyState).toLowerCase()}`
        );
        const detail = document.createElement('span');
        detail.className = 'asset-warranty-detail';
        detail.textContent = warrantyDetail(asset);
        wrapper.append(badge, detail);
        return wrapper;
    }

    function warrantyDetail(asset) {
        const days = Number(asset.warrantyRemainingDays);
        if (asset.warrantyState === 'EXPIRA_EM_BREVE' && Number.isFinite(days)) {
            if (days === 0) return 'Vence hoje';
            return `${days} ${days === 1 ? 'dia restante' : 'dias restantes'}`;
        }
        if (asset.warrantyState === 'VIGENTE') {
            return asset.warrantyEndDate ? `Até ${formatDate(asset.warrantyEndDate)}` : 'Cobertura ativa';
        }
        if (asset.warrantyState === 'EXPIRADA' && Number.isFinite(days)) {
            const elapsed = Math.abs(days);
            return `Há ${elapsed} ${elapsed === 1 ? 'dia' : 'dias'}`;
        }
        if (asset.warrantyState === 'NAO_ELEGIVEL') return 'Ativo fora de operação';
        return 'Sem data cadastrada';
    }

    function openModal(asset = null) {
        if (!canEdit) return;
        editingAsset = asset;
        lastFocusedElement = document.activeElement;
        elements.form.reset();
        hideFormError();
        elements.status.value = 'ATIVO';

        if (asset) {
            elements.modalTitle.textContent = 'Editar ativo';
            elements.modalDescription.textContent = 'Atualize os dados do equipamento sem alterar seu proprietário.';
            elements.submit.textContent = 'Salvar alterações';
            elements.model.value = assetModel(asset);
            elements.manufacturer.value = asset.fabricante || '';
            elements.type.value = asset.tipo || '';
            elements.serial.value = assetSerial(asset);
            elements.status.value = asset.status || 'ATIVO';
            elements.purchaseDate.value = asset.purchaseDate || '';
            elements.warrantyEndDate.value = asset.warrantyEndDate || '';
            elements.warrantyProvider.value = asset.warrantyProvider || '';
            if (isManager) {
                ensureOwnerOption(asset.clienteId);
                elements.owner.value = asset.clienteId;
                elements.owner.disabled = true;
                elements.ownerHelper.textContent = 'O proprietário é imutável após o cadastro.';
            }
        } else {
            elements.modalTitle.textContent = 'Novo ativo';
            elements.modalDescription.textContent = 'Cadastre os dados de inventário e garantia do equipamento.';
            elements.submit.textContent = 'Salvar ativo';
            if (isManager) {
                elements.owner.disabled = false;
                elements.owner.value = '';
                elements.ownerHelper.textContent = 'O proprietário não poderá ser trocado após o cadastro.';
            }
        }

        elements.modal.hidden = false;
        elements.modal.removeAttribute('inert');
        elements.modal.setAttribute('aria-hidden', 'false');
        window.requestAnimationFrame(() => elements.modal.classList.add('active'));
        document.body.classList.add('modal-open');
        elements.model.focus();
    }

    function ensureOwnerOption(clientId) {
        if ([...elements.owner.options].some(option => option.value === clientId)) return;
        const owner = ownerFor(clientId);
        const option = document.createElement('option');
        option.value = clientId;
        option.textContent = owner.name;
        elements.owner.appendChild(option);
    }

    function closeModal() {
        elements.modal.classList.remove('active');
        elements.modal.setAttribute('aria-hidden', 'true');
        elements.modal.setAttribute('inert', '');
        elements.modal.hidden = true;
        document.body.classList.remove('modal-open');
        elements.form.reset();
        hideFormError();
        editingAsset = null;
        if (lastFocusedElement instanceof HTMLElement) lastFocusedElement.focus();
    }

    function buildPayload() {
        const ownerId = isManager
            ? (editingAsset?.clienteId || elements.owner.value)
            : session.id;
        return {
            modelo: elements.model.value.trim(),
            fabricante: emptyToNull(elements.manufacturer.value),
            tipo: elements.type.value,
            serial: elements.serial.value.trim(),
            status: elements.status.value,
            purchaseDate: elements.purchaseDate.value || null,
            warrantyEndDate: elements.warrantyEndDate.value || null,
            warrantyProvider: emptyToNull(elements.warrantyProvider.value),
            clienteId: ownerId
        };
    }

    function validatePayload(payload) {
        if (!payload.clienteId) return 'Selecione o cliente proprietário.';
        if (payload.purchaseDate && payload.warrantyEndDate && payload.warrantyEndDate < payload.purchaseDate) {
            return 'O fim da garantia não pode ser anterior à data de compra.';
        }
        return '';
    }

    async function submitAsset(event) {
        event.preventDefault();
        hideFormError();
        const payload = buildPayload();
        const validationMessage = validatePayload(payload);
        if (validationMessage) {
            showFormError(validationMessage);
            return;
        }

        const defaultText = elements.submit.textContent;
        elements.submit.disabled = true;
        elements.submit.textContent = editingAsset ? 'Salvando...' : 'Cadastrando...';
        try {
            await api.request(editingAsset ? `/assets/${editingAsset.id}` : '/assets', {
                method: editingAsset ? 'PUT' : 'POST',
                body: JSON.stringify(payload)
            });
            const successMessage = editingAsset
                ? 'Ativo atualizado com sucesso.'
                : 'Ativo cadastrado com sucesso.';
            closeModal();
            showToast(successMessage, 'success');
            await loadAssets();
        } catch (error) {
            showFormError(error.message || 'Não foi possível salvar o ativo.');
        } finally {
            elements.submit.disabled = false;
            elements.submit.textContent = defaultText;
        }
    }

    function showFormError(message) {
        elements.formError.textContent = message;
        elements.formError.hidden = false;
    }

    function hideFormError() {
        elements.formError.textContent = '';
        elements.formError.hidden = true;
    }

    function showToast(message, tone = '') {
        const toast = document.createElement('div');
        toast.className = `toast ${tone}`.trim();
        toast.textContent = message;
        elements.toastRegion.appendChild(toast);
        window.setTimeout(() => toast.remove(), 4200);
    }

    elements.filters.addEventListener('submit', event => {
        event.preventDefault();
        loadAssets();
    });

    elements.clearFilters.addEventListener('click', () => {
        elements.query.value = '';
        elements.filters.reset();
        loadAssets();
    });

    elements.query.addEventListener('input', () => {
        window.clearTimeout(searchTimer);
        searchTimer = window.setTimeout(loadAssets, 380);
    });

    elements.query.addEventListener('keydown', event => {
        if (event.key === 'Enter') {
            event.preventDefault();
            window.clearTimeout(searchTimer);
            loadAssets();
        }
    });

    elements.openModalButton.addEventListener('click', () => openModal());
    elements.closeModalButton.addEventListener('click', closeModal);
    elements.cancelModalButton.addEventListener('click', closeModal);
    elements.modal.addEventListener('click', event => {
        if (event.target === elements.modal) closeModal();
    });
    elements.form.addEventListener('submit', submitAsset);

    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && elements.modal.classList.contains('active')) {
            closeModal();
        }
    });
});

function assetModel(asset) {
    return asset?.modelo || asset?.nome || 'Ativo sem modelo';
}

function assetSerial(asset) {
    return asset?.serial || asset?.numeroSerie || '';
}

function assetTypeGlyph(type) {
    return ({
        NOTEBOOK: 'NB',
        DESKTOP: 'PC',
        MONITOR: 'MN',
        IMPRESSORA: 'IM',
        SERVIDOR: 'SV',
        EQUIPAMENTO_REDE: 'RD',
        PERIFERICO: 'PF',
        OUTRO: 'AT'
    })[type] || 'AT';
}

function emptyToNull(value) {
    const normalized = String(value || '').trim();
    return normalized || null;
}

function compactId(value) {
    return value ? `ID ${String(value).slice(0, 8)}` : 'Proprietário não informado';
}

function humanize(value) {
    if (!value) return 'Não informado';
    const normalized = String(value).toLocaleLowerCase('pt-BR').replaceAll('_', ' ');
    return normalized.charAt(0).toLocaleUpperCase('pt-BR') + normalized.slice(1);
}

function formatDate(value) {
    if (!value) return 'Não informada';
    const date = new Date(`${value}T12:00:00`);
    if (Number.isNaN(date.getTime())) return value;
    return new Intl.DateTimeFormat('pt-BR').format(date);
}
