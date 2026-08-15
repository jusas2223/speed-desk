// URL base da API
const API_URL = 'http://localhost:8080/api/tickets';

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
        const userName = user.nome || user.name || 'Usuário';
        document.getElementById('welcomeMessage').textContent = `Bem-vindo(a), ${userName}`;
    } catch (e) {
        localStorage.removeItem('user');
        window.location.href = 'index.html';
        return;
    }

    // 2. Lógica de Logout
    document.getElementById('logoutBtn').addEventListener('click', () => {
        localStorage.removeItem('user');
        window.location.href = 'index.html';
    });

    // 3. Referências DOM
    const colNovos = document.getElementById('col-novos-cards');
    const colAndamento = document.getElementById('col-andamento-cards');
    const colConcluidos = document.getElementById('col-concluidos-cards');
    
    const countNovos = document.getElementById('count-novos');
    const countAndamento = document.getElementById('count-andamento');
    const countConcluidos = document.getElementById('count-concluidos');

    // 4. Buscar e Renderizar Tickets
    async function loadTickets() {
        try {
            // Filtragem role-based: Se cliente, busca apenas os dele.
            // Para TECNICO ou GERENTE/ADMIN, busca todos (ou endpoint apropriado).
            let url = API_URL;
            const role = user.role ? user.role.toUpperCase() : 'CLIENTE';
            if (role === 'CLIENTE') {
                url += `?clienteId=\${user.id}`;
            }

            const response = await fetch(url);
            if (!response.ok) throw new Error('Falha ao buscar chamados da API.');
            const tickets = await response.json();
            
            renderKanban(tickets);
        } catch (error) {
            console.error('Erro ao carregar tickets:', error);
            // Fallback visual
            document.getElementById('dashboardContent').innerHTML = '<p style="color:var(--error-color);">Erro ao carregar painel.</p>';
        }
    }

    function renderKanban(tickets) {
        // Limpar colunas antes de renderizar
        colNovos.innerHTML = '';
        colAndamento.innerHTML = '';
        colConcluidos.innerHTML = '';

        let cNovos = 0, cAndamento = 0, cConcluidos = 0;

        tickets.forEach(ticket => {
            const card = createCard(ticket);
            
            if (ticket.status === 'RECEBIDO') {
                colNovos.appendChild(card);
                cNovos++;
            } else if (ticket.status === 'EM_ATENDIMENTO') {
                colAndamento.appendChild(card);
                cAndamento++;
            } else if (ticket.status === 'RESOLVIDO' || ticket.status === 'FECHADO') {
                colConcluidos.appendChild(card);
                cConcluidos++;
            } else {
                // Fallback para status desconhecidos
                colNovos.appendChild(card);
                cNovos++;
            }
        });

        // Atualizar os contadores numéricos
        countNovos.textContent = cNovos;
        countAndamento.textContent = cAndamento;
        countConcluidos.textContent = cConcluidos;
    }

    function createCard(ticket) {
        const div = document.createElement('div');
        div.className = 'ticket-card';
        
        // Determinar cor da badge com base na prioridade
        let priorityClass = 'p-baixa';
        if (ticket.prioridade === 'ALTA') priorityClass = 'p-alta';
        else if (ticket.prioridade === 'MEDIA') priorityClass = 'p-media';

        // Evita quebrar se cliente for null
        const clientName = ticket.cliente ? ticket.cliente.nome : (ticket.clienteId ? `Cliente #${ticket.clienteId}` : 'N/A');
        
        // Tratar ID e status
        const role = user.role ? user.role.toUpperCase() : '';
        const ticketId = ticket.id || '-';
        const ticketStatus = ticket.status || 'NOVO';

        let htmlCard = `
            <div class="ticket-title">#${ticketId} - ${ticket.titulo}</div>
            <div class="ticket-client">👤 ${clientName}</div>
            <div class="ticket-badges">
                <span class="badge ${priorityClass}">${ticket.prioridade || 'BAIXA'}</span>
                <span class="badge badge-status">${ticketStatus}</span>
            </div>
        `;

        // Botões dinâmicos role-based
        if (role === 'TECNICO' || role === 'GERENTE' || role === 'ADMIN') {
            if (ticketStatus === 'RECEBIDO') {
                htmlCard += `
                    <button class="btn btn-assumir btn-primary" onclick="assumirChamado('${ticketId}')" style="margin-top: 12px; padding: 6px; font-size: 0.8rem;">
                        Assumir
                    </button>
                `;
            } else if (ticketStatus === 'EM_ATENDIMENTO') {
                htmlCard += `
                    <button class="btn btn-resolver btn-primary" onclick="resolverChamado('${ticketId}')" style="margin-top: 12px; padding: 6px; font-size: 0.8rem; background-color: #24a148; border-color: #24a148;">
                        Resolver
                    </button>
                `;
            }
        }

        div.innerHTML = htmlCard;
        return div;
    }

    // Ações do Kanban globais para os botões inline
    window.assumirChamado = async (ticketId) => {
        try {
            const response = await fetch(`${API_URL}/${ticketId}/assumir/${user.id}`, { method: 'PATCH' });
            if (!response.ok) {
                const err = await response.json().catch(()=>({}));
                throw new Error(err.message || 'Erro ao assumir');
            }
            loadTickets(); // Recarrega o Kanban
        } catch (error) {
            console.error('Falha no PATCH assumir:', error);
            alert('Falha ao assumir chamado. Verifique o console.');
        }
    };

    window.resolverChamado = async (ticketId) => {
        try {
            const response = await fetch(`${API_URL}/${ticketId}/resolver`, { method: 'PATCH' });
            if (!response.ok) {
                const err = await response.json().catch(()=>({}));
                throw new Error(err.message || 'Erro ao resolver');
            }
            loadTickets(); // Recarrega o Kanban
        } catch (error) {
            console.error('Falha no PATCH resolver:', error);
            alert('Falha ao resolver chamado. Verifique o console.');
        }
    };

    // 5. Lógica do Modal de Novo Chamado
    const modal = document.getElementById('newTicketModal');
    const btnOpenModal = document.getElementById('btnOpenModal');
    const btnCloseModal = document.getElementById('btnCloseModal');
    const btnCancelModal = document.getElementById('btnCancelModal');
    const newTicketForm = document.getElementById('newTicketForm');

    async function openModal() {
        modal.classList.add('active');
        // Buscar equipamentos do usuário para popular o select
        try {
            const res = await fetch(\`http://localhost:8080/api/assets/cliente/\${user.id}\`);
            const select = document.getElementById('assetSelect');
            select.innerHTML = '<option value="">Nenhum / Não listado</option>'; // Limpa opções antigas
            
            if (res.ok) {
                const assets = await res.json();
                assets.forEach(a => {
                    const opt = document.createElement('option');
                    opt.value = a.id;
                    opt.textContent = \`\${a.nome} (\${a.numeroSerie || 'Sem NS'})\`;
                    select.appendChild(opt);
                });
            }
        } catch (error) {
            console.error('Erro ao buscar equipamentos para o form:', error);
        }
    }

    function closeModal() {
        modal.classList.remove('active');
        newTicketForm.reset();
    }

    btnOpenModal.addEventListener('click', openModal);
    btnCloseModal.addEventListener('click', closeModal);
    btnCancelModal.addEventListener('click', closeModal);

    // Fechar o modal ao clicar na área escura (overlay)
    modal.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
    });

    // 6. Submeter o formulário (POST Novo Chamado)
    newTicketForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const btnSubmit = document.getElementById('btnSubmitTicket');
        btnSubmit.disabled = true;
        btnSubmit.textContent = 'Salvando...';

        const prioridadeBruta = document.getElementById('prioridade').value;
        // Blindagem: Remove acentos, garante uppercase
        const prioridadeLimpa = prioridadeBruta.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toUpperCase().trim();

        const assetIdStr = document.getElementById('assetSelect').value;

        const novoChamado = {
            titulo: document.getElementById('titulo').value,
            descricao: document.getElementById('descricao').value,
            prioridade: prioridadeLimpa,
            clienteId: user.id // Mantém como String (UUID completo)
        };

        if (assetIdStr) {
            novoChamado.assetId = assetIdStr; // Adiciona o vínculo do equipamento se existir
        }

        console.log("Auditoria Payload Novo Chamado:", JSON.stringify(novoChamado, null, 2));

        try {
            const response = await fetch(API_URL, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(novoChamado)
            });

            if (!response.ok) {
                const errData = await response.json().catch(() => ({}));
                console.error("Dados de Erro do Servidor:", errData);
                throw new Error(errData.message || `Erro HTTP: ${response.status}`);
            }

            // Se der sucesso, fecha o modal e recarrega a lista
            closeModal();
            loadTickets(); 

        } catch (error) {
            console.error('Erro na requisição ao servidor:', error);
        } finally {
            // Restaura o botão
            btnSubmit.disabled = false;
            btnSubmit.textContent = 'Salvar Chamado';
        }
    });

    // Inicia buscando os dados assim que a página estiver pronta
    loadTickets();
});
