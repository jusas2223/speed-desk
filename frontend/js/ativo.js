import api from './api.js';

const TYPE_LABELS = Object.freeze({
    NOTEBOOK: 'Notebook', DESKTOP: 'Desktop', MONITOR: 'Monitor', IMPRESSORA: 'Impressora',
    SERVIDOR: 'Servidor', EQUIPAMENTO_REDE: 'Equipamento de rede', PERIFERICO: 'Periférico', OUTRO: 'Outro'
});
const TYPE_TONES = Object.freeze({
    NOTEBOOK: 'brand', DESKTOP: 'brand', MONITOR: 'info', IMPRESSORA: 'warning',
    SERVIDOR: 'danger', EQUIPAMENTO_REDE: 'cyan', PERIFERICO: 'neutral', OUTRO: 'neutral'
});
const STATUS_LABELS = Object.freeze({
    ATIVO: 'Ativo', EM_MANUTENCAO: 'Em manutenção', INATIVO: 'Inativo', DESCARTADO: 'Descartado'
});
const WARRANTY_LABELS = Object.freeze({
    VIGENTE: 'Garantia vigente', EXPIRA_EM_BREVE: 'Garantia expira em breve', EXPIRADA: 'Garantia expirada',
    NAO_INFORMADA: 'Garantia não informada', NAO_ELEGIVEL: 'Não elegível à garantia'
});
const TICKET_STATUS_LABELS = Object.freeze({
    RECEBIDO: 'Recebido', EM_TRIAGEM: 'Em triagem', EM_ATENDIMENTO: 'Em atendimento',
    AGUARDANDO_CLIENTE: 'Aguardando cliente', AGUARDANDO_PECA: 'Aguardando peça',
    RESOLVIDO: 'Resolvido', FECHADO: 'Fechado'
});
const PRIORITY_LABELS = Object.freeze({ BAIXA: 'Baixa', NORMAL: 'Normal', ALTA: 'Alta', CRITICA: 'Crítica' });
const HISTORY_TYPE_LABELS = Object.freeze({
    MANUTENCAO: 'Atualização técnica', ETAPA: 'Etapa de manutenção', CHECKLIST: 'Checklist pós-reparo'
});
const STAGE_LABELS = Object.freeze({
    RECEBIDO: 'Recebido', EM_ANALISE: 'Em análise', EM_REPARO: 'Em reparo', EM_TESTE: 'Em teste', CONCLUIDO: 'Concluído'
});

document.addEventListener('DOMContentLoaded', async () => {
    const session = api.requireAuth();
    if (!session) return;

    const role = String(session.role || '').toUpperCase();
    const canEdit = role === 'CLIENTE' || role === 'GERENTE';
    const params = new URLSearchParams(window.location.search);
    const assetId = params.get('id');
    const clientsById = new Map([[session.id, session]]);

    const elements = {
        loading: document.getElementById('assetDetailLoading'),
        error: document.getElementById('assetDetailError'),
        errorMessage: document.getElementById('assetDetailErrorMessage'),
        content: document.getElementById('assetDetailContent'),
        editButton: document.getElementById('editAssetBtn'),
        typeBadge: document.getElementById('assetTypeBadge'),
        statusBadge: document.getElementById('assetStatusBadge'),
        modelTitle: document.getElementById('assetModelTitle'),
        heroMeta: document.getElementById('assetHeroMeta'),
        idCode: document.getElementById('assetIdCode'),
        inventory: document.getElementById('assetInventoryFields'),
        tickets: document.getElementById('assetTickets'),
        history: document.getElementById('assetTechnicalHistory'),
        warrantyCard: document.getElementById('assetWarrantyCard'),
        warrantyTitle: document.getElementById('assetWarrantyTitle'),
        warrantyDescription: document.getElementById('assetWarrantyDescription'),
        warrantyProgress: document.getElementById('assetWarrantyProgress'),
        warrantyDate: document.getElementById('assetWarrantyDate'),
        warrantyProviderText: document.getElementById('assetWarrantyProviderText'),
        ownerName: document.getElementById('assetOwnerName'),
        ownerMeta: document.getElementById('assetOwnerMeta'),
        createdAt: document.getElementById('assetCreatedAt'),
        updatedAt: document.getElementById('assetUpdatedAt'),
        version: document.getElementById('assetVersion'),
        modal: document.getElementById('editAssetModal'),
        closeModal: document.getElementById('closeEditAssetModal'),
        cancelModal: document.getElementById('cancelEditAsset'),
        form: document.getElementById('editAssetForm'),
        model: document.getElementById('editAssetModel'),
        manufacturer: document.getElementById('editAssetManufacturer'),
        type: document.getElementById('editAssetType'),
        serial: document.getElementById('editAssetSerial'),
        status: document.getElementById('editAssetStatus'),
        owner: document.getElementById('editAssetOwner'),
        purchaseDate: document.getElementById('editAssetPurchaseDate'),
        warrantyEndDate: document.getElementById('editAssetWarrantyEndDate'),
        warrantyProvider: document.getElementById('editAssetWarrantyProvider'),
        formError: document.getElementById('editAssetFormError'),
        submit: document.getElementById('submitEditAsset'),
        toastRegion: document.getElementById('toastRegion')
    };

    let asset = null;
    let lastFocusedElement = null;

    if (!isUuid(assetId)) {
        showPageError('O identificador do ativo está ausente ou é inválido.');
        return;
    }

    await loadPage();

    async function loadPage() {
        showLoading();
        try {
            asset = await api.request(`/assets/${encodeURIComponent(assetId)}`);
            renderAsset(asset);
            showContent();
            await loadRelatedData();
        } catch (error) {
            showPageError(error.message || 'Não foi possível carregar os dados do ativo.');
        }
    }

    async function loadRelatedData() {
        renderRelatedState(elements.tickets, 'Carregando chamados relacionados...');
        renderRelatedState(elements.history, 'Carregando histórico técnico...');

        const requests = [
            api.request(`/assets/${encodeURIComponent(assetId)}/tickets`),
            api.request(`/assets/${encodeURIComponent(assetId)}/technical-history`)
        ];
        if (role === 'GERENTE') requests.push(api.request('/users'));

        const results = await Promise.allSettled(requests);
        if (role === 'GERENTE' && results[2]?.status === 'fulfilled') {
            (Array.isArray(results[2].value) ? results[2].value : []).forEach(user => clientsById.set(user.id, user));
            renderOwner(asset.clienteId);
        }

        if (results[0].status === 'fulfilled') {
            renderTickets(Array.isArray(results[0].value) ? results[0].value : []);
        } else {
            renderRelatedState(elements.tickets, results[0].reason?.message || 'Não foi possível carregar os chamados.', true);
        }

        if (results[1].status === 'fulfilled') {
            renderHistory(Array.isArray(results[1].value) ? results[1].value : []);
        } else {
            renderRelatedState(elements.history, results[1].reason?.message || 'Não foi possível carregar o histórico técnico.', true);
        }
    }

    function renderAsset(item) {
        const model = assetModel(item);
        document.title = `${model} — Speed Desk`;
        elements.typeBadge.textContent = TYPE_LABELS[item.tipo] || humanize(item.tipo);
        elements.typeBadge.className = `asset-type-badge tone-${TYPE_TONES[item.tipo] || 'neutral'}`;
        elements.statusBadge.textContent = STATUS_LABELS[item.status] || humanize(item.status);
        elements.statusBadge.className = `asset-status-badge status-${String(item.status).toLowerCase()}`;
        elements.modelTitle.textContent = model;
        elements.heroMeta.textContent = [item.fabricante, assetSerial(item)].filter(Boolean).join(' • ') || 'Dados de inventário';
        elements.idCode.textContent = item.id;
        elements.editButton.hidden = !canEdit;

        renderInventory(item);
        renderWarranty(item);
        renderOwner(item.clienteId);
        elements.createdAt.textContent = formatDateTime(item.createdAt);
        elements.updatedAt.textContent = formatDateTime(item.updatedAt);
        elements.version.textContent = item.version === null || item.version === undefined ? '—' : `v${item.version}`;
    }

    function renderInventory(item) {
        const fields = [
            ['Modelo', assetModel(item)],
            ['Fabricante', item.fabricante || 'Não informado'],
            ['Tipo', TYPE_LABELS[item.tipo] || humanize(item.tipo)],
            ['Número de série', assetSerial(item) || 'Não informado'],
            ['Situação', STATUS_LABELS[item.status] || humanize(item.status)],
            ['Data de compra', formatDate(item.purchaseDate)],
            ['Fim da garantia', formatDate(item.warrantyEndDate)],
            ['Fornecedor da garantia', item.warrantyProvider || 'Não informado']
        ];
        elements.inventory.replaceChildren(...fields.map(([label, value]) => createDetailField(label, value)));
    }

    function renderWarranty(item) {
        const state = item.warrantyState || 'NAO_INFORMADA';
        elements.warrantyCard.className = `asset-warranty-card warranty-card-${state.toLowerCase()}`;
        elements.warrantyTitle.textContent = WARRANTY_LABELS[state] || humanize(state);
        elements.warrantyDescription.textContent = warrantyDescription(item);
        elements.warrantyDate.textContent = formatDate(item.warrantyEndDate);
        elements.warrantyProviderText.textContent = item.warrantyProvider || 'Não informado';
        elements.warrantyProgress.style.width = `${warrantyProgress(item)}%`;
    }

    function renderOwner(clientId) {
        if (clientId === session.id) {
            elements.ownerName.textContent = session.name;
            elements.ownerMeta.textContent = role === 'CLIENTE' ? `${session.email} • Você` : session.email;
            return;
        }
        const owner = clientsById.get(clientId);
        elements.ownerName.textContent = owner?.name || 'Cliente vinculado';
        elements.ownerMeta.textContent = owner?.email || `ID ${String(clientId || '').slice(0, 8)}`;
    }

    function renderTickets(tickets) {
        if (tickets.length === 0) {
            renderRelatedState(elements.tickets, 'Este ativo ainda não possui chamados relacionados.', false, true);
            return;
        }
        elements.tickets.replaceChildren(...tickets.map(ticket => {
            const link = document.createElement('a');
            link.className = 'asset-related-ticket';
            link.href = `chamado.html?id=${encodeURIComponent(ticket.id)}`;

            const top = document.createElement('span');
            top.className = 'asset-related-ticket-top';
            const code = document.createElement('code');
            code.textContent = ticketCode(ticket.id);
            const status = document.createElement('span');
            status.className = `asset-ticket-status ticket-status-${String(ticket.status).toLowerCase()}`;
            status.textContent = TICKET_STATUS_LABELS[ticket.status] || humanize(ticket.status);
            top.append(code, status);

            const title = document.createElement('strong');
            title.textContent = ticket.titulo || 'Chamado sem título';
            const meta = document.createElement('span');
            meta.className = 'asset-related-ticket-meta';
            meta.textContent = [
                TYPE_LABELS[ticket.ticketType] || humanize(ticket.ticketType),
                PRIORITY_LABELS[ticket.prioridade] || humanize(ticket.prioridade),
                formatDateTime(ticket.dataCriacao)
            ].join(' • ');
            link.append(top, title, meta);
            return link;
        }));
    }

    function renderHistory(history) {
        if (history.length === 0) {
            renderRelatedState(elements.history, 'Nenhuma manutenção de hardware foi registrada para este ativo.', false, true);
            return;
        }
        elements.history.replaceChildren(...history.map(entry => {
            const item = document.createElement('article');
            item.className = 'asset-history-item';
            const marker = document.createElement('span');
            marker.className = `asset-history-marker history-${String(entry.entryType).toLowerCase()}`;
            marker.setAttribute('aria-hidden', 'true');
            const body = document.createElement('div');
            const heading = document.createElement('div');
            heading.className = 'asset-history-heading';
            const title = document.createElement('strong');
            title.textContent = HISTORY_TYPE_LABELS[entry.entryType] || humanize(entry.entryType);
            const time = document.createElement('time');
            time.dateTime = entry.createdAt || '';
            time.textContent = formatDateTime(entry.createdAt);
            heading.append(title, time);
            const description = document.createElement('p');
            description.textContent = entry.description || 'Atualização técnica registrada.';
            const meta = document.createElement('span');
            meta.className = 'asset-history-meta';
            const actor = entry.performedBy?.name || 'Equipe técnica';
            meta.textContent = [entry.ticketCode, STAGE_LABELS[entry.maintenanceStage] || humanize(entry.maintenanceStage), actor]
                .filter(value => value && value !== 'Não informado')
                .join(' • ');
            if (entry.ticketId) {
                const ticketLink = document.createElement('a');
                ticketLink.href = `chamado.html?id=${encodeURIComponent(entry.ticketId)}`;
                ticketLink.textContent = entry.ticketTitle || 'Abrir chamado relacionado';
                ticketLink.className = 'asset-history-ticket-link';
                body.append(heading, description, meta, ticketLink);
            } else {
                body.append(heading, description, meta);
            }
            item.append(marker, body);
            return item;
        }));
    }

    function renderRelatedState(container, message, error = false, empty = false) {
        const state = document.createElement('div');
        state.className = `asset-related-state${error ? ' is-error' : ''}${empty ? ' is-empty' : ''}`;
        state.textContent = message;
        container.replaceChildren(state);
    }

    function showLoading() {
        elements.loading.hidden = false;
        elements.error.hidden = true;
        elements.content.hidden = true;
        elements.editButton.hidden = true;
    }

    function showContent() {
        elements.loading.hidden = true;
        elements.error.hidden = true;
        elements.content.hidden = false;
    }

    function showPageError(message) {
        elements.loading.hidden = true;
        elements.content.hidden = true;
        elements.error.hidden = false;
        elements.errorMessage.textContent = message;
        elements.editButton.hidden = true;
    }

    function openEditModal() {
        if (!asset || !canEdit) return;
        lastFocusedElement = document.activeElement;
        hideFormError();
        elements.model.value = assetModel(asset);
        elements.manufacturer.value = asset.fabricante || '';
        elements.type.value = asset.tipo || 'OUTRO';
        elements.serial.value = assetSerial(asset);
        elements.status.value = asset.status || 'ATIVO';
        elements.owner.value = ownerDisplay(asset.clienteId);
        elements.purchaseDate.value = asset.purchaseDate || '';
        elements.warrantyEndDate.value = asset.warrantyEndDate || '';
        elements.warrantyProvider.value = asset.warrantyProvider || '';
        elements.modal.hidden = false;
        elements.modal.removeAttribute('inert');
        elements.modal.setAttribute('aria-hidden', 'false');
        window.requestAnimationFrame(() => elements.modal.classList.add('active'));
        document.body.classList.add('modal-open');
        elements.model.focus();
    }

    function closeEditModal() {
        elements.modal.classList.remove('active');
        elements.modal.setAttribute('aria-hidden', 'true');
        elements.modal.setAttribute('inert', '');
        elements.modal.hidden = true;
        document.body.classList.remove('modal-open');
        hideFormError();
        if (lastFocusedElement instanceof HTMLElement) lastFocusedElement.focus();
    }

    function ownerDisplay(clientId) {
        if (clientId === session.id) return session.name;
        return clientsById.get(clientId)?.name || `Cliente ${String(clientId || '').slice(0, 8)}`;
    }

    async function updateAsset(event) {
        event.preventDefault();
        hideFormError();
        const payload = {
            modelo: elements.model.value.trim(),
            fabricante: emptyToNull(elements.manufacturer.value),
            tipo: elements.type.value,
            serial: elements.serial.value.trim(),
            status: elements.status.value,
            purchaseDate: elements.purchaseDate.value || null,
            warrantyEndDate: elements.warrantyEndDate.value || null,
            warrantyProvider: emptyToNull(elements.warrantyProvider.value),
            clienteId: asset.clienteId
        };
        if (payload.purchaseDate && payload.warrantyEndDate && payload.warrantyEndDate < payload.purchaseDate) {
            showFormError('O fim da garantia não pode ser anterior à data de compra.');
            return;
        }

        elements.submit.disabled = true;
        elements.submit.textContent = 'Salvando...';
        try {
            asset = await api.request(`/assets/${encodeURIComponent(assetId)}`, {
                method: 'PUT',
                body: JSON.stringify(payload)
            });
            renderAsset(asset);
            closeEditModal();
            showToast('Ativo atualizado com sucesso.', 'success');
        } catch (error) {
            showFormError(error.message || 'Não foi possível salvar as alterações.');
        } finally {
            elements.submit.disabled = false;
            elements.submit.textContent = 'Salvar alterações';
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

    elements.editButton.addEventListener('click', openEditModal);
    elements.closeModal.addEventListener('click', closeEditModal);
    elements.cancelModal.addEventListener('click', closeEditModal);
    elements.modal.addEventListener('click', event => {
        if (event.target === elements.modal) closeEditModal();
    });
    elements.form.addEventListener('submit', updateAsset);
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && elements.modal.classList.contains('active')) closeEditModal();
    });
});

function createDetailField(label, value) {
    const wrapper = document.createElement('div');
    wrapper.className = 'asset-detail-field';
    const term = document.createElement('dt');
    term.textContent = label;
    const description = document.createElement('dd');
    description.textContent = value;
    wrapper.append(term, description);
    return wrapper;
}

function assetModel(asset) {
    return asset?.modelo || asset?.nome || 'Ativo sem modelo';
}

function assetSerial(asset) {
    return asset?.serial || asset?.numeroSerie || '';
}

function warrantyDescription(asset) {
    const days = Number(asset.warrantyRemainingDays);
    if (asset.warrantyState === 'EXPIRA_EM_BREVE' && Number.isFinite(days)) {
        if (days === 0) return 'A cobertura termina hoje. Priorize qualquer solicitação elegível.';
        return `Restam ${days} ${days === 1 ? 'dia' : 'dias'} de cobertura. Planeje o atendimento dentro do prazo.`;
    }
    if (asset.warrantyState === 'VIGENTE') {
        return Number.isFinite(days) ? `Cobertura ativa por mais ${days} dias.` : 'A cobertura deste ativo está vigente.';
    }
    if (asset.warrantyState === 'EXPIRADA' && Number.isFinite(days)) {
        const elapsed = Math.abs(days);
        return `A cobertura terminou há ${elapsed} ${elapsed === 1 ? 'dia' : 'dias'}.`;
    }
    if (asset.warrantyState === 'NAO_ELEGIVEL') {
        return 'Ativos inativos ou descartados não são elegíveis ao atendimento em garantia.';
    }
    return 'Cadastre a data final para acompanhar alertas de garantia.';
}

function warrantyProgress(asset) {
    const days = Number(asset.warrantyRemainingDays);
    if (asset.warrantyState === 'EXPIRADA' || asset.warrantyState === 'NAO_ELEGIVEL') return 0;
    if (asset.warrantyState === 'EXPIRA_EM_BREVE' && Number.isFinite(days)) return Math.max(4, Math.min(30, days));
    if (asset.warrantyState === 'VIGENTE' && Number.isFinite(days)) return Math.max(30, Math.min(100, Math.round(days / 3.65)));
    return 0;
}

function ticketCode(id) {
    return `SPD-${String(id || '').replaceAll('-', '').slice(0, 6).toUpperCase()}`;
}

function emptyToNull(value) {
    const normalized = String(value || '').trim();
    return normalized || null;
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

function formatDateTime(value) {
    if (!value) return 'Não informado';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(date);
}

function isUuid(value) {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(String(value || ''));
}
