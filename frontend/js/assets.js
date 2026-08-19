import api from './api.js';

document.addEventListener('DOMContentLoaded', () => {
    // 1. Verificação de Login
    const session = api.requireAuth();
    if (!session) return;

    if (session.role !== 'CLIENTE') {
        window.location.href = 'dashboard.html';
        return;
    }

    // 2. Logout
    document.getElementById('logoutBtn').addEventListener('click', () => {
        api.logout();
    });

    // 3. Referências DOM
    const assetsTableBody = document.getElementById('assetsTableBody');

    // 4. Buscar Equipamentos (GET)
    async function loadAssets() {
        try {
            assetsTableBody.innerHTML = '<tr><td colspan="3" style="padding: 12px; text-align: center;">Carregando equipamentos...</td></tr>';
            const assets = await api.request(`/assets/cliente/${session.id}`);
            renderAssets(assets);
        } catch (error) {
            console.error('Erro ao carregar equipamentos:', error);

            assetsTableBody.innerHTML = '';
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            td.colSpan = 3;
            td.style.color = 'var(--error-color)';
            td.textContent = 'Não foi possível carregar os equipamentos.';
            tr.appendChild(td);
            assetsTableBody.appendChild(tr);
        }
    }

    function renderAssets(assets) {
        assetsTableBody.innerHTML = '';

        if (!assets || assets.length === 0) {
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            td.colSpan = 3;
            td.textContent = 'Nenhum equipamento cadastrado.';
            tr.appendChild(td);
            assetsTableBody.appendChild(tr);
            return;
        }

        assets.forEach(asset => {
            const tr = document.createElement('tr');
            tr.style.borderBottom = '1px solid var(--border-color)';

            const tdNome = document.createElement('td');
            tdNome.style.padding = '12px';
            tdNome.textContent = asset.nome || 'Sem Nome';
            tr.appendChild(tdNome);

            const tdTipo = document.createElement('td');
            tdTipo.style.padding = '12px';
            tdTipo.textContent = asset.tipo || 'N/A';
            tr.appendChild(tdTipo);

            const tdNS = document.createElement('td');
            tdNS.style.padding = '12px';
            tdNS.textContent = asset.numeroSerie || 'N/A';
            tr.appendChild(tdNS);

            assetsTableBody.appendChild(tr);
        });
    }

    // 5. Lógica do Modal
    const modal = document.getElementById('newAssetModal');
    const btnOpenModal = document.getElementById('btnOpenModal');
    const btnCloseModal = document.getElementById('btnCloseModal');
    const btnCancelModal = document.getElementById('btnCancelModal');
    const newAssetForm = document.getElementById('newAssetForm');

    function openModal() { modal.classList.add('active'); }
    function closeModal() { modal.classList.remove('active'); newAssetForm.reset(); }

    if (btnOpenModal) btnOpenModal.addEventListener('click', openModal);
    if (btnCloseModal) btnCloseModal.addEventListener('click', closeModal);
    if (btnCancelModal) btnCancelModal.addEventListener('click', closeModal);

    if (modal) {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) closeModal();
        });
    }

    // 6. Submeter (POST Novo Equipamento)
    if (newAssetForm) {
        newAssetForm.addEventListener('submit', async (e) => {
            e.preventDefault();

            const btnSubmit = document.getElementById('btnSubmitAsset');
            btnSubmit.disabled = true;
            btnSubmit.textContent = 'Salvando...';

            const novoEquipamento = {
                nome: document.getElementById('nome').value.trim(),
                tipo: document.getElementById('tipo').value.trim(),
                numeroSerie: document.getElementById('numeroSerie').value.trim(),
                clienteId: session.id
            };

            try {
                await api.request('/assets', {
                    method: 'POST',
                    body: JSON.stringify(novoEquipamento)
                });

                closeModal();
                loadAssets();
            } catch (error) {
                console.error('Falha ao registrar equipamento:', error);
                alert(error.message || 'Falha ao registrar equipamento.');
            } finally {
                btnSubmit.disabled = false;
                btnSubmit.textContent = 'Salvar';
            }
        });
    }

    // Inicializa
    loadAssets();
});
