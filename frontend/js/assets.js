import api from './api.js';

document.addEventListener('DOMContentLoaded', () => {
    const session = api.requireAuth();
    if (!session) return;
    if (String(session.role).toUpperCase() !== 'CLIENTE') {
        window.location.href = 'dashboard.html';
        return;
    }

    const tableBody = document.getElementById('assetsTableBody');
    const assetCount = document.getElementById('assetCount');
    const modal = document.getElementById('newAssetModal');
    const form = document.getElementById('newAssetForm');
    const submitButton = document.getElementById('btnSubmitAsset');
    const toastRegion = document.getElementById('toastRegion');

    function showToast(message, tone = '') {
        const toast = document.createElement('div');
        toast.className = `toast ${tone}`.trim();
        toast.textContent = message;
        toastRegion.appendChild(toast);
        window.setTimeout(() => toast.remove(), 3600);
    }

    function renderMessage(message, error = false) {
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 3;
        cell.textContent = message;
        if (error) cell.style.color = 'var(--danger)';
        row.appendChild(cell);
        tableBody.replaceChildren(row);
    }

    function renderAssets(assets) {
        tableBody.replaceChildren();
        assetCount.textContent = `${assets.length} cadastrado${assets.length === 1 ? '' : 's'}`;

        if (assets.length === 0) {
            renderMessage('Nenhum equipamento cadastrado. Use o botão acima para adicionar o primeiro.');
            return;
        }

        assets.forEach(asset => {
            const row = document.createElement('tr');
            [asset.nome, asset.tipo, asset.numeroSerie || 'Não informado'].forEach(value => {
                const cell = document.createElement('td');
                cell.textContent = value;
                row.appendChild(cell);
            });
            tableBody.appendChild(row);
        });
    }

    async function loadAssets() {
        renderMessage('Carregando equipamentos...');
        try {
            const response = await api.request(`/assets/cliente/${session.id}`);
            renderAssets(Array.isArray(response) ? response : []);
        } catch (error) {
            renderMessage(error.message || 'Não foi possível carregar os equipamentos.', true);
        }
    }

    function openModal() {
        modal.hidden = false;
        modal.removeAttribute('inert');
        modal.setAttribute('aria-hidden', 'false');
        modal.classList.add('active');
        document.getElementById('nome').focus();
    }

    function closeModal() {
        modal.classList.remove('active');
        modal.setAttribute('aria-hidden', 'true');
        modal.setAttribute('inert', '');
        modal.hidden = true;
        form.reset();
    }

    document.getElementById('btnOpenModal').addEventListener('click', openModal);
    document.getElementById('btnCloseModal').addEventListener('click', closeModal);
    document.getElementById('btnCancelModal').addEventListener('click', closeModal);
    modal.addEventListener('click', event => {
        if (event.target === modal) closeModal();
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && modal.classList.contains('active')) closeModal();
    });

    form.addEventListener('submit', async event => {
        event.preventDefault();
        submitButton.disabled = true;
        const defaultText = submitButton.textContent;
        submitButton.textContent = 'Salvando...';

        const payload = {
            nome: document.getElementById('nome').value.trim(),
            tipo: document.getElementById('tipo').value.trim(),
            numeroSerie: document.getElementById('numeroSerie').value.trim(),
            clienteId: session.id
        };

        try {
            await api.request('/assets', {
                method: 'POST',
                body: JSON.stringify(payload)
            });
            closeModal();
            showToast('Equipamento adicionado com sucesso.', 'success');
            await loadAssets();
        } catch (error) {
            showToast(error.message || 'Não foi possível salvar o equipamento.', 'error');
        } finally {
            submitButton.disabled = false;
            submitButton.textContent = defaultText;
        }
    });

    loadAssets();
});
