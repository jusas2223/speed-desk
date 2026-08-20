import api from './api.js';

const TICKET_TYPE_LABELS = Object.freeze({
    GERAL: 'Geral',
    HARDWARE: 'Hardware',
    SOFTWARE: 'Software'
});

document.addEventListener('DOMContentLoaded', () => {
    // 1. Verificação de Login
    const session = api.requireAuth();
    if (!session) return; // requireAuth já redireciona

    const userName = session.name || 'Usuário';
    const role = session.role ? session.role.toUpperCase() : 'CLIENTE';
    document.getElementById('welcomeMessage').textContent = `Bem-vindo(a), ${userName} | Perfil: ${role}`;

    // 2. Lógica de Logout
    document.getElementById('logoutBtn').addEventListener('click', () => {
        api.logout();
    });

    // 3. Referências DOM e Ocultação de Funcionalidades
    const colNovos = document.getElementById('col-novos-cards');
    const colAndamento = document.getElementById('col-andamento-cards');
    const colConcluidos = document.getElementById('col-concluidos-cards');

    const countNovos = document.getElementById('count-novos');
    const countAndamento = document.getElementById('count-andamento');
    const countConcluidos = document.getElementById('count-concluidos');

    const menuAssets = document.getElementById('menuAssets');
    const menuSettings = document.getElementById('menuSettings');
    const headerActions = document.getElementById('headerActions');

    // Esconder botões e menus de acordo com o papel
    if (role !== 'CLIENTE') {
        if (menuAssets) menuAssets.style.display = 'none';
        if (headerActions) headerActions.style.display = 'none';
    }

    if (menuSettings) {
        menuSettings.hidden = role !== 'GERENTE';
    }

    // Referências para Modal de Atribuição (Gerente)
    const assignModal = document.getElementById('assignModal');
    const btnCloseAssignModal = document.getElementById('btnCloseAssignModal');
    const btnCancelAssignModal = document.getElementById('btnCancelAssignModal');
    const assignForm = document.getElementById('assignForm');
    const technicianSelect = document.getElementById('technicianSelect');
    const assignTicketId = document.getElementById('assignTicketId');
    const btnSubmitAssign = document.getElementById('btnSubmitAssign');
    let techniciansLoaded = false;

    function syncAssignButtonState() {
        if (btnSubmitAssign) {
            btnSubmitAssign.disabled = !technicianSelect.value;
        }
    }

    if (technicianSelect) {
        technicianSelect.addEventListener('change', syncAssignButtonState);
    }

    // 4. Buscar e Renderizar Tickets
    async function loadTickets() {
        try {
            colNovos.innerHTML = '<div style="padding: 10px; color: var(--text-secondary);">Carregando chamados...</div>';
            const tickets = await api.request('/tickets');
            renderKanban(tickets);
        } catch (error) {
            console.error('Erro ao carregar tickets:', error);
            const mainContent = document.querySelector('.main-content');
            if(mainContent) {
                 colNovos.textContent = 'Erro ao carregar painel.';
            }
        }
    }

    function renderKanban(tickets) {
        // Limpar colunas antes de renderizar
        colNovos.innerHTML = '';
        colAndamento.innerHTML = '';
        colConcluidos.innerHTML = '';

        let cNovos = 0, cAndamento = 0, cConcluidos = 0;

        if (!tickets || tickets.length === 0) {
            countNovos.textContent = '0';
            countAndamento.textContent = '0';
            countConcluidos.textContent = '0';

            const msgNovos = document.createElement('div');
            msgNovos.textContent = 'Nenhum chamado encontrado.';
            msgNovos.style.padding = '10px';
            msgNovos.style.color = 'var(--text-secondary)';
            colNovos.appendChild(msgNovos);

            const msgAndamento = document.createElement('div');
            msgAndamento.textContent = 'Nenhum chamado encontrado.';
            msgAndamento.style.padding = '10px';
            msgAndamento.style.color = 'var(--text-secondary)';
            colAndamento.appendChild(msgAndamento);

            const msgConcluidos = document.createElement('div');
            msgConcluidos.textContent = 'Nenhum chamado encontrado.';
            msgConcluidos.style.padding = '10px';
            msgConcluidos.style.color = 'var(--text-secondary)';
            colConcluidos.appendChild(msgConcluidos);
            return;
        }

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
        else if (ticket.prioridade === 'NORMAL') priorityClass = 'p-normal';
        else if (ticket.prioridade === 'CRITICA') priorityClass = 'p-critica';

        const clientName = ticket.cliente ? ticket.cliente.name : (ticket.clienteId ? `Cliente #${ticket.clienteId}` : 'N/A');

        const ticketId = ticket.id || '-';
        const ticketStatus = ticket.status || 'NOVO';

        // Título e ID
        const titleDiv = document.createElement('div');
        titleDiv.className = 'ticket-title';
        titleDiv.textContent = `#${ticketId} - ${ticket.titulo}`;
        div.appendChild(titleDiv);

        // Cliente
        const clientDiv = document.createElement('div');
        clientDiv.className = 'ticket-client';
        clientDiv.textContent = `👤 ${clientName}`;
        div.appendChild(clientDiv);

        // Badges
        const badgesDiv = document.createElement('div');
        badgesDiv.className = 'ticket-badges';

        const badgePrioridade = document.createElement('span');
        badgePrioridade.className = `badge ${priorityClass}`;
        badgePrioridade.textContent = ticket.prioridade || 'BAIXA';
        badgesDiv.appendChild(badgePrioridade);

        const badgeStatus = document.createElement('span');
        badgeStatus.className = 'badge badge-status';
        badgeStatus.textContent = ticketStatus;
        badgesDiv.appendChild(badgeStatus);

        const ticketType = ticket.ticketType || 'GERAL';
        const badgeTicketType = document.createElement('span');
        badgeTicketType.className = 'badge badge-ticket-type';
        badgeTicketType.textContent = TICKET_TYPE_LABELS[ticketType] || ticketType;
        badgesDiv.appendChild(badgeTicketType);

        div.appendChild(badgesDiv);

        if (ticket.category && ticket.category.name) {
            const categoryDiv = document.createElement('div');
            categoryDiv.className = 'ticket-category';
            categoryDiv.textContent = `Categoria: ${ticket.category.name}`;
            div.appendChild(categoryDiv);
        }

        // Botões dinâmicos role-based
        if (role === 'TECNICO' || role === 'GERENTE') {
            if (ticketStatus === 'RECEBIDO') {
                const btnAssumir = document.createElement('button');
                btnAssumir.className = 'btn btn-assumir btn-primary';
                btnAssumir.style = 'margin-top: 12px; padding: 6px; font-size: 0.8rem;';
                btnAssumir.textContent = role === 'GERENTE' ? 'Atribuir Técnico' : 'Assumir';
                btnAssumir.addEventListener('click', () => {
                    if (role === 'GERENTE') {
                        openAssignModal(ticketId);
                    } else if (role === 'TECNICO') {
                        assumirChamado(ticketId);
                    }
                });
                div.appendChild(btnAssumir);
            } else if (ticketStatus === 'EM_ATENDIMENTO') {
                // Para exibir o botão de Resolver, Gerente sempre vê. Técnico vê se for o responsável.
                const isTecnicoResponsavel = ticket.tecnico && ticket.tecnico.id === session.id;

                if (role === 'GERENTE' || (role === 'TECNICO' && isTecnicoResponsavel)) {
                    const btnResolver = document.createElement('button');
                    btnResolver.className = 'btn btn-resolver btn-primary';
                    btnResolver.style = 'margin-top: 12px; padding: 6px; font-size: 0.8rem; background-color: #24a148; border-color: #24a148;';
                    btnResolver.textContent = 'Resolver';
                    btnResolver.addEventListener('click', () => resolverChamado(ticketId));
                    div.appendChild(btnResolver);
                }
            }
        }

        return div;
    }

    // Ações do Kanban
    async function assumirChamado(ticketId) {
        if (role !== 'TECNICO') return;
        try {
            await api.request(`/tickets/${ticketId}/assumir/${session.id}`, { method: 'PATCH' });
            loadTickets(); // Recarrega o Kanban
        } catch (error) {
            console.error('Falha no PATCH assumir:', error);
            alert(error.message || 'Falha ao assumir chamado.');
        }
    }

    async function resolverChamado(ticketId) {
        try {
            await api.request(`/tickets/${ticketId}/resolver`, { method: 'PATCH' });
            loadTickets(); // Recarrega o Kanban
        } catch (error) {
            console.error('Falha no PATCH resolver:', error);
            alert(error.message || 'Falha ao resolver chamado.');
        }
    }

    // Modal de Atribuição (Gerente)
    async function openAssignModal(ticketId) {
        if (role !== 'GERENTE') return;

        assignTicketId.value = ticketId;
        assignModal.classList.add('active');
        syncAssignButtonState();

        if (!techniciansLoaded) {
            try {
                technicianSelect.innerHTML = '<option value="">Carregando técnicos...</option>';
                syncAssignButtonState();

                const users = await api.request('/users');
                const tecnicos = users.filter(u => u.role === 'TECNICO');

                technicianSelect.innerHTML = '';

                if (tecnicos.length === 0) {
                    technicianSelect.innerHTML = '<option value="">Nenhum técnico disponível</option>';
                } else {
                    technicianSelect.innerHTML = '<option value="">Selecione um técnico</option>';
                    tecnicos.forEach(t => {
                        const opt = document.createElement('option');
                        opt.value = t.id;
                        opt.textContent = `${t.name} (${t.email})`;
                        technicianSelect.appendChild(opt);
                    });
                    techniciansLoaded = true;
                }
                syncAssignButtonState();
            } catch (err) {
                console.error("Erro ao carregar técnicos:", err);
                technicianSelect.innerHTML = '<option value="">Erro ao carregar técnicos</option>';
                syncAssignButtonState();
            }
        }
    }

    function closeAssignModal() {
        if (assignModal) assignModal.classList.remove('active');
        if (assignForm) assignForm.reset();
        syncAssignButtonState();
    }

    if (btnCloseAssignModal) btnCloseAssignModal.addEventListener('click', closeAssignModal);
    if (btnCancelAssignModal) btnCancelAssignModal.addEventListener('click', closeAssignModal);
    if (assignModal) {
        assignModal.addEventListener('click', (e) => {
            if (e.target === assignModal) closeAssignModal();
        });
    }

    if (assignForm) {
        assignForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            if (role !== 'GERENTE') return;

            const tecId = technicianSelect.value;
            const tId = assignTicketId.value;

            if (!tecId) {
                alert("Selecione um técnico.");
                return;
            }

            btnSubmitAssign.disabled = true;
            btnSubmitAssign.textContent = 'Atribuindo...';

            try {
                await api.request(`/tickets/${tId}/assumir/${tecId}`, { method: 'PATCH' });
                closeAssignModal();
                loadTickets();
            } catch (error) {
                alert(error.message || 'Falha ao atribuir chamado.');
            } finally {
                syncAssignButtonState();
                btnSubmitAssign.textContent = 'Confirmar';
            }
        });
    }

    // 5. Lógica do Modal de Novo Chamado
    const modal = document.getElementById('newTicketModal');
    const btnOpenModal = document.getElementById('btnOpenModal');
    const btnCloseModal = document.getElementById('btnCloseModal');
    const btnCancelModal = document.getElementById('btnCancelModal');
    const newTicketForm = document.getElementById('newTicketForm');
    const btnSubmitTicket = document.getElementById('btnSubmitTicket');
    const assetSelect = document.getElementById('assetSelect');
    const ticketTypeSelect = document.getElementById('ticketType');
    const categorySelect = document.getElementById('categorySelect');
    const categoryStatus = document.getElementById('categoryStatus');

    let ticketCategories = [];
    let ticketCategoriesReady = false;
    let ticketCategoriesLoading = false;

    function createSelectOption(value, label) {
        const option = document.createElement('option');
        option.value = value;
        option.textContent = label;
        return option;
    }

    function setCategoryStatus(message, isError = false) {
        categoryStatus.textContent = message;
        categoryStatus.classList.toggle('error', isError);
    }

    function resetCategoryOptions(disabled = true) {
        categorySelect.replaceChildren(createSelectOption('', 'Sem categoria'));
        categorySelect.value = '';
        categorySelect.disabled = disabled;
    }

    function renderCategoryOptions() {
        const selectedCategoryId = categorySelect.value;
        const selectedType = ticketTypeSelect.value || 'GERAL';
        const compatibleCategories = ticketCategories.filter(category => (
            category
            && category.active !== false
            && category.ticketType === selectedType
        ));

        const options = [createSelectOption('', 'Sem categoria')];
        compatibleCategories.forEach(category => {
            options.push(createSelectOption(category.id, category.name));
        });

        categorySelect.replaceChildren(...options);
        categorySelect.disabled = false;

        const selectionRemainsCompatible = compatibleCategories.some(category => (
            category.id === selectedCategoryId
        ));
        categorySelect.value = selectionRemainsCompatible ? selectedCategoryId : '';

        if (compatibleCategories.length === 0) {
            setCategoryStatus('Nenhuma categoria ativa disponível para este tipo.');
        } else {
            setCategoryStatus('');
        }
    }

    async function loadTicketCategories() {
        if (ticketCategoriesLoading) return;

        ticketCategoriesLoading = true;
        ticketCategoriesReady = false;
        resetCategoryOptions(true);
        setCategoryStatus('Carregando categorias...');

        try {
            const categories = await api.request('/ticket-categories');
            ticketCategories = Array.isArray(categories) ? categories : [];
            ticketCategoriesReady = true;
            renderCategoryOptions();
        } catch (error) {
            console.error('Erro ao carregar categorias para o chamado:', error);
            ticketCategories = [];
            resetCategoryOptions(true);
            setCategoryStatus(
                'Não foi possível carregar as categorias. Você ainda pode abrir o chamado sem categoria.',
                true
            );
        } finally {
            ticketCategoriesLoading = false;
        }
    }

    async function loadAssetsForTicket() {
        assetSelect.disabled = true;
        assetSelect.replaceChildren(createSelectOption('', 'Carregando...'));

        try {
            const assets = await api.request(`/assets/cliente/${session.id}`);
            const options = [createSelectOption('', 'Nenhum / Não listado')];

            if (Array.isArray(assets)) {
                assets.forEach(asset => {
                    options.push(createSelectOption(
                        asset.id,
                        `${asset.nome} (${asset.numeroSerie || 'Sem NS'})`
                    ));
                });
            }

            assetSelect.replaceChildren(...options);
        } catch (error) {
            console.error('Erro ao buscar equipamentos para o formulário:', error);
            assetSelect.replaceChildren(createSelectOption('', 'Nenhum / Não listado'));
        } finally {
            assetSelect.disabled = false;
        }
    }

    function openModal() {
        if (role !== 'CLIENTE') return;

        modal.classList.add('active');
        loadAssetsForTicket();
        loadTicketCategories();
    }

    function closeModal() {
        if (modal) modal.classList.remove('active');
        if (newTicketForm) {
            newTicketForm.reset();
            ticketTypeSelect.value = 'GERAL';
        }

        if (ticketCategoriesReady) {
            renderCategoryOptions();
        } else {
            resetCategoryOptions(true);
            setCategoryStatus('');
        }
    }

    if (btnOpenModal) btnOpenModal.addEventListener('click', openModal);
    if (btnCloseModal) btnCloseModal.addEventListener('click', closeModal);
    if (btnCancelModal) btnCancelModal.addEventListener('click', closeModal);
    if (ticketTypeSelect) {
        ticketTypeSelect.addEventListener('change', () => {
            if (ticketCategoriesReady) {
                renderCategoryOptions();
            } else {
                resetCategoryOptions(true);
            }
        });
    }

    // Fechar o modal ao clicar na área escura (overlay)
    if (modal) {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) closeModal();
        });
    }

    // 6. Submeter o formulário (POST Novo Chamado)
    if (newTicketForm) {
        newTicketForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            if (role !== 'CLIENTE') return;

            btnSubmitTicket.disabled = true;
            btnSubmitTicket.textContent = 'Salvando...';

            const prioridadeBruta = document.getElementById('prioridade').value;
            const prioridadeLimpa = prioridadeBruta.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toUpperCase().trim();
            const assetId = assetSelect.value;
            const categoryId = categorySelect.disabled ? '' : categorySelect.value;

            const novoChamado = {
                titulo: document.getElementById('titulo').value,
                descricao: document.getElementById('descricao').value,
                prioridade: prioridadeLimpa,
                clienteId: session.id,
                assetId: assetId || null,
                ticketType: ticketTypeSelect.value || 'GERAL'
            };

            if (categoryId) {
                novoChamado.categoryId = categoryId;
            }

            try {
                await api.request('/tickets', {
                    method: 'POST',
                    body: JSON.stringify(novoChamado)
                });

                closeModal();
                await loadTickets();
            } catch (error) {
                console.error('Erro na requisição ao servidor:', error);
                alert(error.message || 'Erro ao criar chamado.');
            } finally {
                btnSubmitTicket.disabled = false;
                btnSubmitTicket.textContent = 'Salvar Chamado';
            }
        });
    }

    // Inicia buscando os dados assim que a página estiver pronta
    loadTickets();
});
