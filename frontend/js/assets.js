const ASSETS_API_URL = 'http://localhost:8080/api/assets';

document.addEventListener('DOMContentLoaded', () => {
    // 1. Verificação de Login
    const userJson = localStorage.getItem('user');
    if (!userJson) {
        window.location.href = 'index.html';
        return;
    }

    let user;
    try {
        user = JSON.parse(userJson);
    } catch (e) {
        localStorage.removeItem('user');
        window.location.href = 'index.html';
        return;
    }

    // 2. Logout
    document.getElementById('logoutBtn').addEventListener('click', () => {
        localStorage.removeItem('user');
        window.location.href = 'index.html';
    });

    // 3. Referências DOM
    const assetsTableBody = document.getElementById('assetsTableBody');

    // 4. Buscar Equipamentos (GET)
    async function loadAssets() {
        try {
            // Rota explícita com variável de rota (Path Variable)
            const response = await fetch(\`\${ASSETS_API_URL}/cliente/\${user.id}\`);
            
            if (!response.ok) throw new Error('Falha ao buscar equipamentos.');
            const assets = await response.json();
            
            renderAssets(assets);
        } catch (error) {
            console.error('Erro ao carregar equipamentos:', error);
            assetsTableBody.innerHTML = '<tr><td colspan="3" style="color: var(--error-color);">Não foi possível carregar os equipamentos.</td></tr>';
        }
    }

    function renderAssets(assets) {
        assetsTableBody.innerHTML = '';

        if (!assets || assets.length === 0) {
            assetsTableBody.innerHTML = '<tr><td colspan="3">Nenhum equipamento cadastrado.</td></tr>';
            return;
        }

        assets.forEach(asset => {
            const tr = document.createElement('tr');
            tr.style.borderBottom = '1px solid var(--border-color)';
            tr.innerHTML = `
                <td style="padding: 12px;">${asset.nome || 'Sem Nome'}</td>
                <td style="padding: 12px;">${asset.tipo || 'N/A'}</td>
                <td style="padding: 12px;">${asset.numeroSerie || 'N/A'}</td>
            `;
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

    btnOpenModal.addEventListener('click', openModal);
    btnCloseModal.addEventListener('click', closeModal);
    btnCancelModal.addEventListener('click', closeModal);
    modal.addEventListener('click', (e) => { if (e.target === modal) closeModal(); });

    // 6. Submeter (POST Novo Equipamento)
    newAssetForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const btnSubmit = document.getElementById('btnSubmitAsset');
        btnSubmit.disabled = true;
        btnSubmit.textContent = 'Salvando...';

        const novoEquipamento = {
            nome: document.getElementById('nome').value.trim(),
            tipo: document.getElementById('tipo').value.trim(),
            numeroSerie: document.getElementById('numeroSerie').value.trim(),
            clienteId: user.id // Mantém como String (UUID completo)
        };

        console.log("Auditoria Payload Equipamento:", JSON.stringify(novoEquipamento, null, 2));

        try {
            const response = await fetch(ASSETS_API_URL, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(novoEquipamento)
            });

            if (!response.ok) {
                const errData = await response.json().catch(() => ({}));
                console.error("Dados de Erro do Servidor (Assets):", errData);
                throw new Error(errData.message || \`Erro HTTP: \${response.status}\`);
            }

            closeModal();
            loadAssets(); 

        } catch (error) {
            console.error('Falha ao registrar equipamento:', error);
            alert('Falha ao registrar equipamento. Verifique o console.');
        } finally {
            btnSubmit.disabled = false;
            btnSubmit.textContent = 'Salvar';
        }
    });

    // Inicializa
    loadAssets();
});
