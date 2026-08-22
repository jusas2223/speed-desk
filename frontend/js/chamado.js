import api from './api.js';

const STATUS_LABELS = Object.freeze({
    RECEBIDO: 'Recebido',
    EM_TRIAGEM: 'Em triagem',
    EM_ATENDIMENTO: 'Em atendimento',
    AGUARDANDO_CLIENTE: 'Aguardando cliente',
    AGUARDANDO_PECA: 'Aguardando peça',
    RESOLVIDO: 'Resolvido',
    FECHADO: 'Fechado'
});

const PRIORITY_LABELS = Object.freeze({
    BAIXA: 'Baixa',
    NORMAL: 'Normal',
    ALTA: 'Alta',
    CRITICA: 'Crítica'
});

const TYPE_LABELS = Object.freeze({
    GERAL: 'Geral',
    HARDWARE: 'Hardware',
    SOFTWARE: 'Software'
});

const HARDWARE_ELIGIBILITY_LABELS = Object.freeze({
    PENDENTE: 'Avaliação pendente',
    ELEGIVEL: 'Atendimento elegível',
    NAO_ELEGIVEL: 'Não elegível'
});

const HARDWARE_WARRANTY_LABELS = Object.freeze({
    NAO_AVALIADA: 'Garantia não avaliada',
    COBERTA: 'Coberto pela garantia',
    NAO_COBERTA: 'Sem cobertura de garantia'
});

const HARDWARE_STAGE_LABELS = Object.freeze({
    RECEBIDO: 'Recebido',
    EM_ANALISE: 'Em análise',
    EM_REPARO: 'Em reparo',
    EM_TESTE: 'Em teste',
    CONCLUIDO: 'Concluído'
});

const HARDWARE_STAGES = Object.freeze([
    'RECEBIDO',
    'EM_ANALISE',
    'EM_REPARO',
    'EM_TESTE',
    'CONCLUIDO'
]);

const HARDWARE_HISTORY_TYPE_LABELS = Object.freeze({
    ETAPA: 'Mudança de etapa',
    MANUTENCAO: 'Registro técnico',
    CHECKLIST: 'Checklist pós-reparo'
});

const SOFTWARE_ENVIRONMENT_LABELS = Object.freeze({
    PRODUCAO: 'Produção',
    HOMOLOGACAO: 'Homologação',
    DESENVOLVIMENTO: 'Desenvolvimento',
    TESTE: 'Teste',
    OUTRO: 'Outro'
});

const SOFTWARE_LOG_LEVELS = Object.freeze(['DEBUG', 'INFO', 'WARN', 'ERROR']);

const STATUS_TRANSITIONS = Object.freeze({
    RECEBIDO: ['EM_TRIAGEM', 'EM_ATENDIMENTO'],
    EM_TRIAGEM: ['EM_ATENDIMENTO', 'AGUARDANDO_CLIENTE'],
    EM_ATENDIMENTO: ['AGUARDANDO_CLIENTE', 'AGUARDANDO_PECA', 'RESOLVIDO'],
    AGUARDANDO_CLIENTE: ['EM_ATENDIMENTO'],
    AGUARDANDO_PECA: ['EM_ATENDIMENTO'],
    RESOLVIDO: [],
    FECHADO: []
});

const CLOSED_STATUSES = new Set(['RESOLVIDO', 'FECHADO']);
const TEAM_ROLES = new Set(['GERENTE', 'TECNICO']);
const ASSIGNABLE_STATUSES = new Set(['RECEBIDO', 'EM_TRIAGEM']);
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function getTicketCode(ticket) {
    const compactId = String(ticket.id || '').replaceAll('-', '').slice(0, 6).toUpperCase();
    return `SPD-${compactId || '000000'}`;
}

function parseDate(value) {
    if (!value) return null;
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
}

function formatDate(value) {
    const date = parseDate(value);
    if (!date) return 'Não informado';
    return new Intl.DateTimeFormat('pt-BR', {
        dateStyle: 'long',
        timeStyle: 'short'
    }).format(date);
}

function formatSeconds(value) {
    const seconds = Math.max(0, Math.abs(Number(value) || 0));
    const totalMinutes = Math.max(1, Math.ceil(seconds / 60));
    if (totalMinutes < 60) return `${totalMinutes} minuto${totalMinutes === 1 ? '' : 's'}`;
    const totalHours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    if (totalHours < 24) {
        const hoursLabel = `${totalHours} hora${totalHours === 1 ? '' : 's'}`;
        return minutes ? `${hoursLabel} e ${minutes} minuto${minutes === 1 ? '' : 's'}` : hoursLabel;
    }
    const days = Math.floor(totalHours / 24);
    const hours = totalHours % 24;
    const daysLabel = `${days} dia${days === 1 ? '' : 's'}`;
    return hours ? `${daysLabel} e ${hours} hora${hours === 1 ? '' : 's'}` : daysLabel;
}

function getSlaInfo(ticket) {
    const hasServerRemaining = ticket.slaRemainingSeconds !== null
        && ticket.slaRemainingSeconds !== undefined
        && Number.isFinite(Number(ticket.slaRemainingSeconds));
    const remaining = hasServerRemaining ? Number(ticket.slaRemainingSeconds) : 0;
    const state = ticket.slaState;

    if (state === 'PAUSED' || ticket.slaPaused) {
        const pausedBalance = hasServerRemaining
            ? (remaining >= 0
                ? `${formatSeconds(remaining)} preservados`
                : `o prazo já estava vencido há ${formatSeconds(remaining)}`)
            : 'saldo indisponível';
        return {
            title: 'SLA pausado',
            description: hasServerRemaining
                ? `${pausedBalance}. Pausa registrada em ${formatDate(ticket.slaPausedAt)}.`
                : `Contagem interrompida em ${formatDate(ticket.slaPausedAt)}.`,
            tone: 'is-paused'
        };
    }
    if (state === 'MET') {
        return {
            title: 'SLA cumprido',
            description: `Atendimento concluído dentro do prazo. Resolução: ${formatDate(ticket.resolvedAt)}.`,
            tone: 'is-complete'
        };
    }
    if (state === 'BREACHED') {
        return {
            title: hasServerRemaining ? `SLA vencido há ${formatSeconds(remaining)}` : 'SLA vencido',
            description: `Prazo previsto para ${formatDate(ticket.dataVencimento)}.`,
            tone: 'is-overdue'
        };
    }
    if (state === 'AT_RISK') {
        return {
            title: hasServerRemaining ? `${formatSeconds(remaining)} restantes` : 'SLA em risco',
            description: `Atenção: o prazo termina em ${formatDate(ticket.dataVencimento)}.`,
            tone: 'is-risk'
        };
    }
    if (state === 'ON_TRACK') {
        return {
            title: hasServerRemaining ? `${formatSeconds(remaining)} restantes` : 'SLA dentro do prazo',
            description: `Prazo previsto para ${formatDate(ticket.dataVencimento)}.`,
            tone: 'is-ok'
        };
    }

    const deadline = parseDate(ticket.dataVencimento);
    if (!deadline) {
        return {
            title: 'Prazo não informado',
            description: 'Este chamado não possui uma data de vencimento disponível.',
            tone: 'is-neutral'
        };
    }
    const differenceSeconds = Math.trunc((deadline.getTime() - Date.now()) / 1000);
    if (CLOSED_STATUSES.has(ticket.status)) {
        return {
            title: 'Chamado finalizado',
            description: `O prazo registrado era ${formatDate(ticket.dataVencimento)}.`,
            tone: 'is-complete'
        };
    }
    if (differenceSeconds <= 0) {
        return {
            title: `SLA vencido há ${formatSeconds(differenceSeconds)}`,
            description: `O prazo terminou em ${formatDate(ticket.dataVencimento)}.`,
            tone: 'is-overdue'
        };
    }
    return {
        title: `${formatSeconds(differenceSeconds)} restantes`,
        description: `Prazo previsto para ${formatDate(ticket.dataVencimento)}.`,
        tone: differenceSeconds <= 86400 ? 'is-risk' : 'is-ok'
    };
}

function statusClass(status) {
    if (status === 'RECEBIDO') return 'status-recebido';
    if (status === 'EM_TRIAGEM') return 'status-triagem';
    if (status === 'EM_ATENDIMENTO') return 'status-atendimento';
    if (status === 'RESOLVIDO') return 'status-resolvido';
    if (status === 'FECHADO') return 'status-fechado';
    return 'status-aguardando';
}

function priorityClass(priority) {
    return {
        BAIXA: 'p-baixa',
        NORMAL: 'p-normal',
        ALTA: 'p-alta',
        CRITICA: 'p-critica'
    }[priority] || 'p-baixa';
}

function createBadge(label, className) {
    const badge = document.createElement('span');
    badge.className = `badge ${className}`;
    badge.textContent = label;
    return badge;
}

function createDetailField(label, value) {
    const wrapper = document.createElement('div');
    wrapper.className = 'detail-field';
    const term = document.createElement('dt');
    term.textContent = label;
    const description = document.createElement('dd');
    description.textContent = value || 'Não informado';
    wrapper.append(term, description);
    return wrapper;
}

function createInfoCard(title, iconPath, fields) {
    const article = document.createElement('article');
    article.className = 'detail-info-card';
    const header = document.createElement('header');
    header.className = 'detail-info-card-header';
    const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    icon.setAttribute('class', 'icon');
    icon.setAttribute('viewBox', '0 0 24 24');
    icon.setAttribute('aria-hidden', 'true');
    icon.innerHTML = iconPath;
    const heading = document.createElement('h2');
    heading.textContent = title;
    header.append(icon, heading);
    const details = document.createElement('dl');
    details.className = 'detail-fields';
    fields.forEach(field => details.appendChild(createDetailField(field.label, field.value)));
    article.append(header, details);
    return article;
}

document.addEventListener('DOMContentLoaded', () => {
    const session = api.requireAuth();
    if (!session) return;

    const role = String(session.role || '').toUpperCase();
    const isTeamMember = TEAM_ROLES.has(role);
    const root = document.getElementById('ticketDetail');
    const ticketId = new URLSearchParams(window.location.search).get('id') || '';
    const actionModal = document.getElementById('ticketActionModal');
    const actionTitle = document.getElementById('ticketActionTitle');
    const actionDescription = document.getElementById('ticketActionDescription');
    const actionFeedback = document.getElementById('ticketActionFeedback');
    const actionConfirm = document.getElementById('confirmTicketAction');
    const pauseField = document.getElementById('ticketPauseReasonField');
    const pauseReason = document.getElementById('ticketPauseReason');
    const toastRegion = document.getElementById('ticketToastRegion');
    const state = {
        ticket: null,
        comments: [],
        commentsLoading: true,
        commentsError: '',
        technicians: [],
        techniciansLoading: false,
        techniciansLoaded: false,
        techniciansError: '',
        selectedTechnicianId: '',
        specialized: {
            loading: false,
            error: '',
            hardware: {
                details: null,
                history: [],
                checklist: null
            },
            software: {
                details: null,
                logs: []
            }
        },
        pendingAction: null,
        lastFocusedElement: null
    };

    function setFeedback(message, tone = '') {
        actionFeedback.textContent = message;
        actionFeedback.className = `feedback ${tone}`.trim();
        actionFeedback.hidden = !message;
    }

    function showToast(message, tone = '') {
        const toast = document.createElement('div');
        toast.className = `toast ${tone}`.trim();
        toast.textContent = message;
        toastRegion.appendChild(toast);
        window.setTimeout(() => toast.remove(), 3800);
    }

    function renderError(title, message) {
        root.replaceChildren();
        root.setAttribute('aria-busy', 'false');
        const card = document.createElement('section');
        card.className = 'page-card detail-state-card';
        const errorState = document.createElement('div');
        errorState.className = 'detail-error-state';
        const heading = document.createElement('h1');
        heading.textContent = title;
        const description = document.createElement('p');
        description.textContent = message;
        const back = document.createElement('a');
        back.className = 'btn btn-primary';
        back.href = 'chamados.html';
        back.textContent = 'Voltar aos chamados';
        errorState.append(heading, description, back);
        card.appendChild(errorState);
        root.appendChild(card);
    }

    function userCanOperate(ticket) {
        return role === 'GERENTE' || (role === 'TECNICO' && ticket.tecnico?.id === session.id);
    }

    function createActionButton(label, tone, action) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = `btn btn-compact ${tone}`;
        button.textContent = label;
        button.addEventListener('click', event => openActionModal(action, event.currentTarget));
        return button;
    }

    function ticketCanBeAssigned(ticket) {
        return !ticket.tecnico && ASSIGNABLE_STATUSES.has(ticket.status);
    }

    function createManagerAssignment(ticket) {
        const panel = document.createElement('form');
        panel.className = 'workflow-assignment';
        panel.setAttribute('aria-label', 'Atribuir responsável técnico');
        const label = document.createElement('label');
        label.className = 'form-label';
        label.htmlFor = 'workflowTechnicianSelect';
        label.textContent = 'Responsável técnico';
        const controls = document.createElement('div');
        controls.className = 'workflow-assignment-controls';
        const select = document.createElement('select');
        select.id = 'workflowTechnicianSelect';
        select.className = 'form-control';
        select.required = true;
        const submit = document.createElement('button');
        submit.type = 'submit';
        submit.className = 'btn btn-primary btn-compact';
        submit.textContent = 'Atribuir';

        if (state.techniciansLoading) {
            select.replaceChildren(new Option('Carregando técnicos...', ''));
            select.disabled = true;
            submit.disabled = true;
        } else if (state.techniciansError) {
            select.replaceChildren(new Option('Falha ao carregar técnicos', ''));
            select.disabled = true;
            submit.type = 'button';
            submit.className = 'btn btn-secondary btn-compact';
            submit.textContent = 'Tentar novamente';
            submit.addEventListener('click', loadTechnicians);
        } else {
            const options = [new Option('Selecione um técnico', '')];
            state.technicians.forEach(technician => {
                options.push(new Option(`${technician.name} · ${technician.email}`, technician.id));
            });
            select.replaceChildren(...options);
            if (state.technicians.some(technician => technician.id === state.selectedTechnicianId)) {
                select.value = state.selectedTechnicianId;
            }
            submit.disabled = !select.value;
            select.disabled = state.technicians.length === 0;
        }
        select.addEventListener('change', () => {
            state.selectedTechnicianId = select.value;
            submit.disabled = !select.value;
        });
        controls.append(select, submit);
        panel.append(label, controls);
        if (!state.techniciansLoading && !state.techniciansError && state.techniciansLoaded && !state.technicians.length) {
            const empty = document.createElement('p');
            empty.className = 'workflow-assignment-status';
            empty.textContent = 'Nenhum técnico ativo está disponível.';
            panel.appendChild(empty);
        }
        if (state.techniciansError) {
            const error = document.createElement('p');
            error.className = 'workflow-assignment-status is-error';
            error.textContent = state.techniciansError;
            panel.appendChild(error);
        }
        panel.addEventListener('submit', event => {
            event.preventDefault();
            const technician = state.technicians.find(item => item.id === select.value);
            if (!technician) {
                select.focus();
                return;
            }
            openActionModal({
                kind: 'assign',
                technicianId: technician.id,
                title: 'Atribuir responsável',
                description: `Confirme a atribuição deste chamado para ${technician.name}. O atendimento será iniciado automaticamente.`,
                confirmLabel: 'Atribuir técnico',
                successMessage: `Chamado atribuído para ${technician.name}.`
            }, submit);
        });
        return panel;
    }

    function createWorkflowCard(ticket) {
        const card = document.createElement('article');
        card.className = 'detail-info-card workflow-card';
        const heading = document.createElement('div');
        heading.className = 'workflow-card-heading';
        const title = document.createElement('h2');
        title.textContent = 'Ações do chamado';
        const subtitle = document.createElement('p');
        subtitle.textContent = 'As transições são validadas pelo servidor.';
        heading.append(title, subtitle);
        const actions = document.createElement('div');
        actions.className = 'workflow-actions';
        const canOperate = userCanOperate(ticket);
        const canAssign = ticketCanBeAssigned(ticket);

        let assignmentPanel = null;
        if (canAssign && role === 'TECNICO') {
            actions.appendChild(createActionButton('Assumir chamado', 'btn-primary', {
                kind: 'assign',
                technicianId: session.id,
                title: 'Assumir chamado',
                description: 'Confirme que você será o responsável por este atendimento. O chamado passará para Em atendimento.',
                confirmLabel: 'Assumir chamado',
                successMessage: 'Chamado assumido com sucesso.'
            }));
        }
        if (canAssign && role === 'GERENTE') {
            assignmentPanel = createManagerAssignment(ticket);
        }

        if (canOperate) {
            (STATUS_TRANSITIONS[ticket.status] || []).forEach(nextStatus => {
                actions.appendChild(createActionButton(
                    `Mover para ${STATUS_LABELS[nextStatus]}`,
                    nextStatus === 'RESOLVIDO' ? 'btn-success' : 'btn-secondary',
                    {
                        kind: 'status',
                        status: nextStatus,
                        title: 'Alterar status do chamado',
                        description: `Confirme a transição de ${STATUS_LABELS[ticket.status]} para ${STATUS_LABELS[nextStatus]}.`,
                        confirmLabel: nextStatus === 'RESOLVIDO' ? 'Marcar como resolvido' : 'Alterar status'
                    }
                ));
            });
        }

        const canCloseOrReopen = role === 'GERENTE' || role === 'CLIENTE';
        if (ticket.status === 'RESOLVIDO' && canCloseOrReopen) {
            actions.appendChild(createActionButton('Fechar chamado', 'btn-success', {
                kind: 'close',
                title: 'Fechar chamado',
                description: 'O fechamento confirma que o atendimento foi concluído. O chamado poderá ser reaberto posteriormente.',
                confirmLabel: 'Fechar chamado'
            }));
            actions.appendChild(createActionButton('Reabrir atendimento', 'btn-secondary', {
                kind: 'reopen',
                title: 'Reabrir atendimento',
                description: 'A resolução será desfeita, o chamado voltará ao fluxo de atendimento e receberá um novo prazo de SLA.',
                confirmLabel: 'Reabrir atendimento'
            }));
        }

        if (ticket.status === 'FECHADO' && canCloseOrReopen) {
            actions.appendChild(createActionButton('Reabrir chamado', 'btn-secondary', {
                kind: 'reopen',
                title: 'Reabrir chamado',
                description: 'O chamado voltará ao fluxo de atendimento e terá o SLA retomado conforme as regras vigentes.',
                confirmLabel: 'Reabrir chamado'
            }));
        }

        if (canOperate && !CLOSED_STATUSES.has(ticket.status)) {
            if (ticket.slaPaused) {
                actions.appendChild(createActionButton('Retomar SLA', 'btn-primary', {
                    kind: 'resume',
                    title: 'Retomar contagem do SLA',
                    description: 'A contagem continuará a partir do tempo preservado no momento da pausa.',
                    confirmLabel: 'Retomar SLA'
                }));
            } else {
                actions.appendChild(createActionButton('Pausar SLA', 'btn-secondary', {
                    kind: 'pause',
                    title: 'Pausar contagem do SLA',
                    description: 'Use a pausa somente quando o atendimento estiver impedido por uma dependência externa. Informe o motivo para registrar a decisão.',
                    confirmLabel: 'Pausar SLA'
                }));
            }
        }

        if (!actions.childElementCount) {
            const empty = document.createElement('p');
            empty.className = 'workflow-empty';
            empty.textContent = role === 'TECNICO' && ticket.tecnico
                ? 'Este chamado está atribuído a outro técnico.'
                : (role === 'TECNICO' && !ticket.tecnico
                    ? 'O chamado está sem responsável, mas não pode ser assumido neste status.'
                    : 'Nenhuma ação está disponível neste status.');
            actions.appendChild(empty);
        }
        card.append(heading);
        if (assignmentPanel) card.appendChild(assignmentPanel);
        card.appendChild(actions);
        return card;
    }

    async function loadTechnicians() {
        if (role !== 'GERENTE') return;
        state.techniciansLoading = true;
        state.techniciansError = '';
        if (state.ticket) renderTicket();
        try {
            const response = await api.request('/users');
            state.technicians = Array.isArray(response)
                ? response
                    .filter(user => user.role === 'TECNICO' && user.active !== false)
                    .sort((left, right) => String(left.name).localeCompare(String(right.name), 'pt-BR'))
                : [];
            state.techniciansLoaded = true;
        } catch (error) {
            console.error('Erro ao carregar técnicos:', error);
            state.technicians = [];
            state.techniciansLoaded = false;
            state.techniciansError = error.message || 'Não foi possível carregar os técnicos ativos.';
        } finally {
            state.techniciansLoading = false;
            if (state.ticket) renderTicket();
        }
    }

    function createCommentCard(comment, internal) {
        const article = document.createElement('article');
        article.className = `ticket-comment ${internal ? 'is-internal' : 'is-public'}`;
        const header = document.createElement('header');
        const author = document.createElement('div');
        const authorName = document.createElement('strong');
        authorName.textContent = comment.author?.name || 'Usuário';
        const authorMeta = document.createElement('span');
        authorMeta.textContent = comment.author?.email || 'Conta do Speed Desk';
        author.append(authorName, authorMeta);
        const date = document.createElement('time');
        const createdAt = comment.createdAt || comment.dataCriacao;
        date.dateTime = createdAt || '';
        date.textContent = formatDate(createdAt);
        header.append(author, date);
        const content = document.createElement('p');
        content.textContent = comment.content || '';
        article.append(header, content);
        return article;
    }

    function createCommentColumn({ internal, title, description, placeholder }) {
        const section = document.createElement('section');
        section.className = `comment-channel ${internal ? 'is-internal' : 'is-public'}`;
        const header = document.createElement('header');
        const heading = document.createElement('h3');
        heading.textContent = title;
        const copy = document.createElement('p');
        copy.textContent = description;
        header.append(heading, copy);

        const list = document.createElement('div');
        list.className = 'ticket-comments-list';
        const comments = state.comments.filter(comment => Boolean(comment.internal) === internal);
        if (state.commentsLoading) {
            const loading = document.createElement('p');
            loading.className = 'comment-empty';
            loading.textContent = 'Carregando mensagens...';
            list.appendChild(loading);
        } else if (state.commentsError) {
            const error = document.createElement('p');
            error.className = 'comment-empty is-error';
            error.textContent = state.commentsError;
            list.appendChild(error);
        } else if (!comments.length) {
            const empty = document.createElement('p');
            empty.className = 'comment-empty';
            empty.textContent = internal ? 'Nenhuma nota interna registrada.' : 'Nenhum comentário público registrado.';
            list.appendChild(empty);
        } else {
            comments.forEach(comment => list.appendChild(createCommentCard(comment, internal)));
        }

        const form = document.createElement('form');
        form.className = 'comment-composer';
        const label = document.createElement('label');
        const textareaId = internal ? 'internalCommentContent' : 'publicCommentContent';
        label.className = 'form-label';
        label.htmlFor = textareaId;
        label.textContent = internal ? 'Nova nota interna' : 'Novo comentário';
        const textarea = document.createElement('textarea');
        textarea.id = textareaId;
        textarea.className = 'form-control';
        textarea.rows = 4;
        textarea.maxLength = 4000;
        textarea.required = true;
        textarea.placeholder = placeholder;
        const footer = document.createElement('div');
        footer.className = 'comment-composer-footer';
        const hint = document.createElement('span');
        hint.textContent = internal ? 'Visível apenas para gerente e técnicos.' : 'Visível ao solicitante e à equipe.';
        const submit = document.createElement('button');
        submit.type = 'submit';
        submit.className = `btn btn-compact ${internal ? 'btn-secondary' : 'btn-primary'}`;
        submit.textContent = internal ? 'Adicionar nota' : 'Enviar comentário';
        footer.append(hint, submit);
        form.append(label, textarea, footer);
        form.addEventListener('submit', event => submitComment(event, internal, textarea, submit));
        section.append(header, list, form);
        return section;
    }

    function createCommentsSection() {
        const section = document.createElement('section');
        section.className = 'ticket-detail-section ticket-conversation-section';
        const heading = document.createElement('div');
        heading.className = 'conversation-heading';
        const title = document.createElement('h2');
        title.textContent = 'Comunicação do chamado';
        const description = document.createElement('p');
        description.textContent = isTeamMember
            ? 'Comentários públicos e notas internas permanecem separados.'
            : 'Use este espaço para conversar com a equipe responsável.';
        heading.append(title, description);
        const channels = document.createElement('div');
        channels.className = `comment-channels ${isTeamMember ? 'has-internal' : ''}`;
        channels.appendChild(createCommentColumn({
            internal: false,
            title: 'Comentários públicos',
            description: 'Comunicação compartilhada com o solicitante.',
            placeholder: 'Escreva uma atualização, dúvida ou resposta...'
        }));
        if (isTeamMember) {
            channels.appendChild(createCommentColumn({
                internal: true,
                title: 'Notas internas',
                description: 'Contexto reservado para a equipe de atendimento.',
                placeholder: 'Registre diagnóstico, hipótese ou orientação interna...'
            }));
        }
        section.append(heading, channels);
        return section;
    }

    function createSpecializedHeading(title, description, eyebrow) {
        const header = document.createElement('header');
        header.className = 'specialized-heading';
        const copy = document.createElement('div');
        const label = document.createElement('span');
        label.className = 'specialized-eyebrow';
        label.textContent = eyebrow;
        const heading = document.createElement('h2');
        heading.textContent = title;
        const paragraph = document.createElement('p');
        paragraph.textContent = description;
        copy.append(label, heading, paragraph);
        header.appendChild(copy);
        return header;
    }

    function createControlGroup(labelText, control, hintText = '') {
        const group = document.createElement('div');
        group.className = 'form-group specialized-form-group';
        const label = document.createElement('label');
        label.className = 'form-label';
        label.htmlFor = control.id;
        label.textContent = labelText;
        group.append(label, control);
        if (hintText) {
            const hint = document.createElement('span');
            hint.className = 'field-hint';
            hint.textContent = hintText;
            group.appendChild(hint);
        }
        return group;
    }

    function createSelectControl(id, options, selectedValue, disabled = false) {
        const select = document.createElement('select');
        select.id = id;
        select.className = 'form-control';
        select.required = true;
        select.disabled = disabled;
        options.forEach(({ value, label }) => select.add(new Option(label, value)));
        select.value = selectedValue || options[0]?.value || '';
        return select;
    }

    function createTextControl(id, value, { multiline = false, maxLength = 160, rows = 4, disabled = false, placeholder = '' } = {}) {
        const control = document.createElement(multiline ? 'textarea' : 'input');
        control.id = id;
        control.className = 'form-control';
        if (!multiline) control.type = 'text';
        if (multiline) control.rows = rows;
        control.maxLength = maxLength;
        control.required = true;
        control.disabled = disabled;
        control.placeholder = placeholder;
        control.value = value || '';
        return control;
    }

    function createInlineFeedback() {
        const feedback = document.createElement('div');
        feedback.className = 'feedback';
        feedback.setAttribute('role', 'alert');
        feedback.setAttribute('aria-live', 'assertive');
        feedback.hidden = true;
        return feedback;
    }

    function updateInlineFeedback(feedback, message, tone = 'error') {
        feedback.textContent = message;
        feedback.className = `feedback ${tone}`;
        feedback.hidden = !message;
    }

    function setSubmitBusy(button, busy, busyLabel = 'Salvando...') {
        if (!button.dataset.defaultLabel) button.dataset.defaultLabel = button.textContent;
        button.disabled = busy;
        button.textContent = busy ? busyLabel : button.dataset.defaultLabel;
    }

    function createSpecializedLoadState() {
        const panel = document.createElement('div');
        panel.className = `specialized-load-state${state.specialized.error ? ' is-error' : ''}`;
        panel.setAttribute('role', state.specialized.error ? 'alert' : 'status');
        const message = document.createElement('p');
        message.textContent = state.specialized.error || 'Carregando informações especializadas...';
        panel.appendChild(message);
        if (state.specialized.error) {
            const retry = document.createElement('button');
            retry.type = 'button';
            retry.className = 'btn btn-secondary btn-compact';
            retry.textContent = 'Tentar novamente';
            retry.addEventListener('click', loadSpecializedData);
            panel.appendChild(retry);
        }
        return panel;
    }

    function createHardwareProgress(currentStage) {
        const list = document.createElement('ol');
        list.className = 'hardware-stage-progress';
        list.setAttribute('aria-label', 'Progresso da manutenção');
        const currentIndex = Math.max(0, HARDWARE_STAGES.indexOf(currentStage));
        HARDWARE_STAGES.forEach((stage, index) => {
            const item = document.createElement('li');
            if (index < currentIndex) item.className = 'is-complete';
            if (index === currentIndex) {
                item.className = 'is-current';
                item.setAttribute('aria-current', 'step');
            }
            const marker = document.createElement('span');
            marker.setAttribute('aria-hidden', 'true');
            marker.textContent = index < currentIndex ? '✓' : String(index + 1);
            const label = document.createElement('strong');
            label.textContent = HARDWARE_STAGE_LABELS[stage];
            item.append(marker, label);
            list.appendChild(item);
        });
        return list;
    }

    function createHardwareSummary(details, checklist) {
        const summary = document.createElement('div');
        summary.className = 'specialized-summary-grid';
        [
            ['Elegibilidade', HARDWARE_ELIGIBILITY_LABELS[details.eligibilityStatus] || details.eligibilityStatus],
            ['Garantia', HARDWARE_WARRANTY_LABELS[details.warrantyCoverage] || details.warrantyCoverage],
            ['Etapa atual', HARDWARE_STAGE_LABELS[details.maintenanceStage] || details.maintenanceStage],
            ['Pós-reparo', checklist.completed ? 'Checklist concluído' : 'Checklist pendente']
        ].forEach(([labelText, valueText]) => {
            const item = document.createElement('div');
            const label = document.createElement('span');
            label.textContent = labelText;
            const value = document.createElement('strong');
            value.textContent = valueText || 'Não informado';
            item.append(label, value);
            summary.appendChild(item);
        });
        return summary;
    }

    function createHardwareDetailsForm(details, checklist, canEdit) {
        const form = document.createElement('form');
        form.id = 'hardwareDetailsForm';
        form.className = 'specialized-form';
        form.setAttribute('aria-label', 'Elegibilidade, garantia e etapa de manutenção');
        const grid = document.createElement('div');
        grid.className = 'specialized-form-grid';

        const eligibility = createSelectControl('hardwareEligibility', Object.entries(HARDWARE_ELIGIBILITY_LABELS)
            .map(([value, label]) => ({ value, label })), details.eligibilityStatus, !canEdit);
        const warranty = createSelectControl('hardwareWarranty', Object.entries(HARDWARE_WARRANTY_LABELS)
            .map(([value, label]) => ({ value, label })), details.warrantyCoverage, !canEdit);
        const currentStage = details.maintenanceStage || 'RECEBIDO';
        const currentIndex = Math.max(0, HARDWARE_STAGES.indexOf(currentStage));
        const availableStages = HARDWARE_STAGES
            .filter((_, index) => index === currentIndex || index === currentIndex + 1)
            .map(value => ({ value, label: HARDWARE_STAGE_LABELS[value] }));
        const stageHint = currentStage === 'EM_TESTE' && !checklist.completed
            ? 'Conclua os cinco itens do checklist antes de avançar para Concluído.'
            : 'As etapas avançam uma posição por vez.';
        const stage = createSelectControl('hardwareStage', availableStages, currentStage, !canEdit);
        const notes = createTextControl('hardwareEligibilityNotes', details.eligibilityNotes, {
            multiline: true,
            maxLength: 2000,
            rows: 4,
            disabled: !canEdit,
            placeholder: 'Registre restrições, cobertura ou justificativa da avaliação...'
        });
        notes.required = false;
        const notesGroup = createControlGroup('Observações de elegibilidade', notes, 'Opcional, até 2.000 caracteres.');
        notesGroup.classList.add('specialized-form-span');

        grid.append(
            createControlGroup('Elegibilidade do atendimento', eligibility),
            createControlGroup('Cobertura da garantia', warranty),
            createControlGroup('Etapa de manutenção', stage, stageHint),
            notesGroup
        );
        form.appendChild(grid);

        if (canEdit) {
            const actions = document.createElement('div');
            actions.className = 'specialized-form-actions';
            const feedback = createInlineFeedback();
            const submit = document.createElement('button');
            submit.type = 'submit';
            submit.className = 'btn btn-primary btn-compact';
            submit.textContent = 'Salvar atendimento';
            actions.append(feedback, submit);
            form.appendChild(actions);
            form.addEventListener('submit', async event => {
                event.preventDefault();
                updateInlineFeedback(feedback, '');
                setSubmitBusy(submit, true);
                try {
                    const updated = await api.request(`/tickets/${encodeURIComponent(ticketId)}/hardware`, {
                        method: 'PUT',
                        body: JSON.stringify({
                            eligibilityStatus: eligibility.value,
                            warrantyCoverage: warranty.value,
                            eligibilityNotes: notes.value.trim() || null,
                            maintenanceStage: stage.value
                        })
                    });
                    state.specialized.hardware.details = updated;
                    try {
                        const history = await api.request(`/tickets/${encodeURIComponent(ticketId)}/hardware/history`);
                        state.specialized.hardware.history = Array.isArray(history) ? history : [];
                    } catch (historyError) {
                        console.error('Atendimento salvo, mas o histórico não pôde ser recarregado:', historyError);
                    }
                    showToast('Atendimento de hardware atualizado.', 'success');
                    renderTicket();
                } catch (error) {
                    updateInlineFeedback(feedback, error.message || 'Não foi possível atualizar o atendimento.');
                    setSubmitBusy(submit, false);
                }
            });
        } else {
            const note = document.createElement('p');
            note.className = 'specialized-permission-note';
            note.textContent = 'Somente o gerente ou o técnico responsável pode alterar estes dados.';
            form.appendChild(note);
        }
        return form;
    }

    function createChecklistItem(id, labelText, checked, disabled) {
        const label = document.createElement('label');
        label.className = 'specialized-check-item';
        const input = document.createElement('input');
        input.id = id;
        input.type = 'checkbox';
        input.checked = Boolean(checked);
        input.disabled = disabled;
        const marker = document.createElement('span');
        marker.setAttribute('aria-hidden', 'true');
        const copy = document.createElement('strong');
        copy.textContent = labelText;
        label.append(input, marker, copy);
        return { label, input };
    }

    function createHardwareChecklist(details, checklist, canOperate) {
        const article = document.createElement('article');
        article.className = 'specialized-subcard';
        const heading = document.createElement('header');
        const title = document.createElement('h3');
        title.textContent = 'Checklist pós-reparo';
        const status = document.createElement('span');
        status.className = `specialized-status-chip ${checklist.completed ? 'is-success' : 'is-pending'}`;
        status.textContent = checklist.completed ? 'Concluído' : 'Pendente';
        heading.append(title, status);
        const allowedStage = ['EM_TESTE', 'CONCLUIDO'].includes(details.maintenanceStage);
        const canEdit = canOperate && allowedStage;
        const form = document.createElement('form');
        form.className = 'specialized-checklist';
        const definitions = [
            ['equipmentTurnsOn', 'Equipamento liga corretamente'],
            ['functionalityValidated', 'Funcionalidade principal validada'],
            ['connectivityValidated', 'Conectividade validada'],
            ['cleaningCompleted', 'Limpeza concluída'],
            ['clientDataPreserved', 'Dados do cliente preservados']
        ];
        const controls = {};
        const checklistGrid = document.createElement('div');
        checklistGrid.className = 'specialized-check-grid';
        definitions.forEach(([key, labelText]) => {
            const item = createChecklistItem(`hardwareChecklist-${key}`, labelText, checklist[key], !canEdit);
            controls[key] = item.input;
            checklistGrid.appendChild(item.label);
        });
        const notes = createTextControl('hardwareChecklistNotes', checklist.notes, {
            multiline: true,
            maxLength: 2000,
            rows: 3,
            disabled: !canEdit,
            placeholder: 'Observações finais do teste e da entrega...'
        });
        notes.required = false;
        form.append(checklistGrid, createControlGroup('Observações do checklist', notes, 'Opcional, até 2.000 caracteres.'));
        if (canEdit) {
            const actions = document.createElement('div');
            actions.className = 'specialized-form-actions';
            const feedback = createInlineFeedback();
            const submit = document.createElement('button');
            submit.type = 'submit';
            submit.className = 'btn btn-primary btn-compact';
            submit.textContent = 'Salvar checklist';
            actions.append(feedback, submit);
            form.appendChild(actions);
            form.addEventListener('submit', async event => {
                event.preventDefault();
                updateInlineFeedback(feedback, '');
                setSubmitBusy(submit, true);
                try {
                    const body = { notes: notes.value.trim() || null };
                    definitions.forEach(([key]) => { body[key] = controls[key].checked; });
                    const updated = await api.request(`/tickets/${encodeURIComponent(ticketId)}/hardware/checklist`, {
                        method: 'PUT',
                        body: JSON.stringify(body)
                    });
                    state.specialized.hardware.checklist = updated;
                    if (updated.completed && !checklist.completed) {
                        try {
                            const history = await api.request(`/tickets/${encodeURIComponent(ticketId)}/hardware/history`);
                            state.specialized.hardware.history = Array.isArray(history) ? history : [];
                        } catch (historyError) {
                            console.error('Checklist salvo, mas o histórico não pôde ser recarregado:', historyError);
                        }
                    }
                    showToast(updated.completed ? 'Checklist pós-reparo concluído.' : 'Checklist atualizado.', 'success');
                    renderTicket();
                } catch (error) {
                    updateInlineFeedback(feedback, error.message || 'Não foi possível salvar o checklist.');
                    setSubmitBusy(submit, false);
                }
            });
        } else {
            const note = document.createElement('p');
            note.className = 'specialized-permission-note';
            note.textContent = canOperate
                ? 'O checklist será liberado quando a manutenção chegar à etapa Em teste.'
                : 'O checklist pode ser alterado somente pelo responsável técnico ou gerente.';
            form.appendChild(note);
        }
        article.append(heading, form);
        return article;
    }

    function createHardwareHistory(history, canOperate) {
        const article = document.createElement('article');
        article.className = 'specialized-subcard';
        const heading = document.createElement('header');
        const title = document.createElement('h3');
        title.textContent = 'Histórico técnico';
        const count = document.createElement('span');
        count.className = 'specialized-count';
        count.textContent = `${history.length} registro${history.length === 1 ? '' : 's'}`;
        heading.append(title, count);
        const list = document.createElement('div');
        list.className = 'specialized-timeline';
        if (!history.length) {
            const empty = document.createElement('p');
            empty.className = 'specialized-empty';
            empty.textContent = 'Nenhum registro técnico foi criado ainda.';
            list.appendChild(empty);
        } else {
            history.forEach(entry => {
                const item = document.createElement('article');
                const marker = document.createElement('span');
                marker.className = `specialized-timeline-marker type-${String(entry.entryType || '').toLowerCase()}`;
                marker.setAttribute('aria-hidden', 'true');
                const content = document.createElement('div');
                const meta = document.createElement('div');
                meta.className = 'specialized-timeline-meta';
                const type = document.createElement('strong');
                type.textContent = HARDWARE_HISTORY_TYPE_LABELS[entry.entryType] || entry.entryType || 'Registro';
                const stage = document.createElement('span');
                stage.textContent = HARDWARE_STAGE_LABELS[entry.maintenanceStage] || entry.maintenanceStage || '';
                const date = document.createElement('time');
                date.dateTime = entry.createdAt || '';
                date.textContent = formatDate(entry.createdAt);
                meta.append(type, stage, date);
                const description = document.createElement('p');
                description.textContent = entry.description || '';
                const actor = document.createElement('small');
                actor.textContent = `Por ${entry.performedBy?.name || 'Equipe Speed Desk'}`;
                content.append(meta, description, actor);
                item.append(marker, content);
                list.appendChild(item);
            });
        }
        article.append(heading, list);

        if (canOperate) {
            const form = document.createElement('form');
            form.className = 'specialized-history-form';
            const description = createTextControl('hardwareHistoryDescription', '', {
                multiline: true,
                maxLength: 4000,
                rows: 3,
                placeholder: 'Descreva o procedimento, teste ou resultado técnico...'
            });
            const field = createControlGroup('Novo registro de manutenção', description, 'Até 4.000 caracteres.');
            const actions = document.createElement('div');
            actions.className = 'specialized-form-actions';
            const feedback = createInlineFeedback();
            const submit = document.createElement('button');
            submit.type = 'submit';
            submit.className = 'btn btn-secondary btn-compact';
            submit.textContent = 'Adicionar ao histórico';
            actions.append(feedback, submit);
            form.append(field, actions);
            form.addEventListener('submit', async event => {
                event.preventDefault();
                const value = description.value.trim();
                if (!value) {
                    description.focus();
                    return;
                }
                updateInlineFeedback(feedback, '');
                setSubmitBusy(submit, true, 'Adicionando...');
                try {
                    const created = await api.request(`/tickets/${encodeURIComponent(ticketId)}/hardware/history`, {
                        method: 'POST',
                        body: JSON.stringify({ description: value })
                    });
                    state.specialized.hardware.history = [...state.specialized.hardware.history, created];
                    showToast('Registro adicionado ao histórico técnico.', 'success');
                    renderTicket();
                } catch (error) {
                    updateInlineFeedback(feedback, error.message || 'Não foi possível adicionar o registro.');
                    setSubmitBusy(submit, false);
                }
            });
            article.appendChild(form);
        }
        return article;
    }

    function createHardwareSection() {
        const section = document.createElement('section');
        section.className = 'ticket-detail-section specialized-section hardware-section';
        section.appendChild(createSpecializedHeading(
            'Atendimento de hardware',
            'Garantia, elegibilidade, manutenção e validação pós-reparo em um fluxo único.',
            'Fluxo técnico HW'
        ));
        if (state.specialized.loading || state.specialized.error) {
            section.appendChild(createSpecializedLoadState());
            return section;
        }
        const details = state.specialized.hardware.details || {
            eligibilityStatus: 'PENDENTE',
            warrantyCoverage: 'NAO_AVALIADA',
            maintenanceStage: 'RECEBIDO'
        };
        const checklist = state.specialized.hardware.checklist || {};
        const history = state.specialized.hardware.history || [];
        const canOperate = userCanOperate(state.ticket);
        section.append(
            createHardwareProgress(details.maintenanceStage),
            createHardwareSummary(details, checklist),
            createHardwareDetailsForm(details, checklist, canOperate)
        );
        const columns = document.createElement('div');
        columns.className = 'specialized-columns';
        columns.append(
            createHardwareChecklist(details, checklist, canOperate),
            createHardwareHistory(history, canOperate)
        );
        section.appendChild(columns);
        return section;
    }

    function createSoftwareDetailsForm(details, canEdit) {
        const form = document.createElement('form');
        form.id = 'softwareDetailsForm';
        form.className = 'specialized-form';
        form.setAttribute('aria-label', 'Contexto técnico do software');
        const grid = document.createElement('div');
        grid.className = 'specialized-form-grid';
        const version = createTextControl('softwareVersion', details.softwareVersion, {
            maxLength: 120,
            disabled: !canEdit,
            placeholder: 'Ex.: 2026.8.1'
        });
        const environmentOptions = [
            { value: '', label: 'Selecione o ambiente' },
            ...Object.entries(SOFTWARE_ENVIRONMENT_LABELS)
                .map(([value, label]) => ({ value, label }))
        ];
        const environment = createSelectControl(
            'softwareEnvironment',
            environmentOptions,
            details.environment || '',
            !canEdit
        );
        const platform = createTextControl('softwarePlatform', details.platform, {
            maxLength: 160,
            disabled: !canEdit,
            placeholder: 'Ex.: Web, desktop, mobile'
        });
        const operatingSystem = createTextControl('softwareOperatingSystem', details.operatingSystem, {
            maxLength: 160,
            disabled: !canEdit,
            placeholder: 'Ex.: Windows 11 24H2'
        });
        grid.append(
            createControlGroup('Versão do software', version),
            createControlGroup('Ambiente afetado', environment),
            createControlGroup('Plataforma', platform),
            createControlGroup('Sistema operacional', operatingSystem)
        );
        const narratives = document.createElement('div');
        narratives.className = 'specialized-narratives';
        const reproduction = createTextControl('softwareReproductionSteps', details.reproductionSteps, {
            multiline: true,
            maxLength: 10000,
            rows: 5,
            disabled: !canEdit,
            placeholder: 'Liste os passos necessários para reproduzir o problema...'
        });
        const expected = createTextControl('softwareExpectedResult', details.expectedResult, {
            multiline: true,
            maxLength: 10000,
            rows: 4,
            disabled: !canEdit,
            placeholder: 'Descreva o comportamento esperado...'
        });
        const actual = createTextControl('softwareActualResult', details.actualResult, {
            multiline: true,
            maxLength: 10000,
            rows: 4,
            disabled: !canEdit,
            placeholder: 'Descreva o que realmente acontece...'
        });
        narratives.append(
            createControlGroup('Passos para reprodução', reproduction),
            createControlGroup('Resultado esperado', expected),
            createControlGroup('Resultado atual', actual)
        );
        form.append(grid, narratives);

        if (canEdit) {
            const actions = document.createElement('div');
            actions.className = 'specialized-form-actions';
            const feedback = createInlineFeedback();
            const submit = document.createElement('button');
            submit.type = 'submit';
            submit.className = 'btn btn-primary btn-compact';
            submit.textContent = details.configured ? 'Salvar contexto técnico' : 'Configurar contexto técnico';
            actions.append(feedback, submit);
            form.appendChild(actions);
            form.addEventListener('submit', async event => {
                event.preventDefault();
                if (!form.reportValidity()) return;
                updateInlineFeedback(feedback, '');
                setSubmitBusy(submit, true);
                try {
                    const updated = await api.request(`/tickets/${encodeURIComponent(ticketId)}/software`, {
                        method: 'PUT',
                        body: JSON.stringify({
                            softwareVersion: version.value.trim(),
                            environment: environment.value,
                            platform: platform.value.trim(),
                            operatingSystem: operatingSystem.value.trim(),
                            reproductionSteps: reproduction.value.trim(),
                            expectedResult: expected.value.trim(),
                            actualResult: actual.value.trim()
                        })
                    });
                    state.specialized.software.details = updated;
                    showToast('Contexto técnico do software atualizado.', 'success');
                    renderTicket();
                } catch (error) {
                    updateInlineFeedback(feedback, error.message || 'Não foi possível salvar o contexto técnico.');
                    setSubmitBusy(submit, false);
                }
            });
        } else {
            const note = document.createElement('p');
            note.className = 'specialized-permission-note';
            note.textContent = 'Somente o solicitante, o técnico responsável ou um gerente pode alterar estes dados.';
            form.appendChild(note);
        }
        return form;
    }

    function createSoftwareLogs(logs, canCreate) {
        const article = document.createElement('article');
        article.className = 'specialized-subcard software-logs-card';
        const heading = document.createElement('header');
        const title = document.createElement('h3');
        title.textContent = 'Logs técnicos estruturados';
        const count = document.createElement('span');
        count.className = 'specialized-count';
        count.textContent = `${logs.length} log${logs.length === 1 ? '' : 's'}`;
        heading.append(title, count);
        const list = document.createElement('div');
        list.className = 'software-log-list';
        if (!logs.length) {
            const empty = document.createElement('p');
            empty.className = 'specialized-empty';
            empty.textContent = 'Nenhum log técnico registrado.';
            list.appendChild(empty);
        } else {
            logs.forEach(log => {
                const item = document.createElement('article');
                item.className = `software-log level-${String(log.level || 'info').toLowerCase()}`;
                const header = document.createElement('header');
                const level = document.createElement('strong');
                level.textContent = log.level || 'INFO';
                const source = document.createElement('code');
                source.textContent = log.source || 'Aplicação';
                const date = document.createElement('time');
                date.dateTime = log.occurredAt || '';
                date.textContent = formatDate(log.occurredAt);
                header.append(level, source, date);
                const message = document.createElement('pre');
                message.textContent = log.message || '';
                item.append(header, message);
                list.appendChild(item);
            });
        }
        article.append(heading, list);

        if (canCreate) {
            const form = document.createElement('form');
            form.className = 'software-log-form';
            const grid = document.createElement('div');
            grid.className = 'software-log-form-grid';
            const level = createSelectControl('softwareLogLevel', SOFTWARE_LOG_LEVELS
                .map(value => ({ value, label: value })), 'INFO');
            const source = createTextControl('softwareLogSource', '', {
                maxLength: 120,
                placeholder: 'Ex.: navegador, API, ERP'
            });
            const occurredAt = document.createElement('input');
            occurredAt.id = 'softwareLogOccurredAt';
            occurredAt.type = 'datetime-local';
            occurredAt.className = 'form-control';
            grid.append(
                createControlGroup('Nível', level),
                createControlGroup('Origem', source),
                createControlGroup('Data e hora', occurredAt, 'Opcional; vazio usa o horário atual.')
            );
            const message = createTextControl('softwareLogMessage', '', {
                multiline: true,
                maxLength: 10000,
                rows: 4,
                placeholder: 'Cole ou descreva a mensagem técnica sem incluir credenciais...'
            });
            const actions = document.createElement('div');
            actions.className = 'specialized-form-actions';
            const feedback = createInlineFeedback();
            const submit = document.createElement('button');
            submit.type = 'submit';
            submit.className = 'btn btn-secondary btn-compact';
            submit.textContent = 'Registrar log';
            actions.append(feedback, submit);
            form.append(grid, createControlGroup('Mensagem', message, 'Até 10.000 caracteres.'), actions);
            form.addEventListener('submit', async event => {
                event.preventDefault();
                if (!form.reportValidity()) return;
                updateInlineFeedback(feedback, '');
                setSubmitBusy(submit, true, 'Registrando...');
                try {
                    const body = {
                        level: level.value,
                        source: source.value.trim(),
                        message: message.value.trim()
                    };
                    if (occurredAt.value) body.occurredAt = new Date(occurredAt.value).toISOString();
                    const created = await api.request(`/tickets/${encodeURIComponent(ticketId)}/software/logs`, {
                        method: 'POST',
                        body: JSON.stringify(body)
                    });
                    state.specialized.software.logs = [created, ...state.specialized.software.logs];
                    showToast('Log técnico registrado.', 'success');
                    renderTicket();
                } catch (error) {
                    updateInlineFeedback(feedback, error.message || 'Não foi possível registrar o log.');
                    setSubmitBusy(submit, false);
                }
            });
            article.appendChild(form);
        }
        return article;
    }

    function createSoftwareSection() {
        const section = document.createElement('section');
        section.className = 'ticket-detail-section specialized-section software-section';
        section.appendChild(createSpecializedHeading(
            'Contexto de software',
            'Ambiente afetado, reprodução do problema e evidências técnicas organizadas.',
            'Fluxo técnico SW'
        ));
        if (state.specialized.loading || state.specialized.error) {
            section.appendChild(createSpecializedLoadState());
            return section;
        }
        const details = state.specialized.software.details || { configured: false };
        const logs = state.specialized.software.logs || [];
        const isOwner = role === 'CLIENTE' && state.ticket.cliente?.id === session.id;
        const canMaintainDetails = role === 'GERENTE' || isOwner || userCanOperate(state.ticket);
        const canCreateLogs = userCanOperate(state.ticket);
        const status = document.createElement('div');
        status.className = 'software-context-status';
        const chip = document.createElement('span');
        chip.className = `specialized-status-chip ${details.configured ? 'is-success' : 'is-pending'}`;
        chip.textContent = details.configured ? 'Contexto configurado' : 'Configuração pendente';
        const update = document.createElement('span');
        update.textContent = details.updatedAt ? `Atualizado em ${formatDate(details.updatedAt)}` : 'Ainda não há dados técnicos salvos.';
        status.append(chip, update);
        section.append(status, createSoftwareDetailsForm(details, canMaintainDetails), createSoftwareLogs(logs, canCreateLogs));
        return section;
    }

    function createSpecializedSection() {
        if (state.ticket.ticketType === 'HARDWARE') return createHardwareSection();
        if (state.ticket.ticketType === 'SOFTWARE') return createSoftwareSection();
        return null;
    }

    async function loadSpecializedData() {
        const type = state.ticket?.ticketType;
        if (type !== 'HARDWARE' && type !== 'SOFTWARE') return;
        state.specialized.loading = true;
        state.specialized.error = '';
        renderTicket();
        try {
            if (type === 'HARDWARE') {
                const [details, history, checklist] = await Promise.all([
                    api.request(`/tickets/${encodeURIComponent(ticketId)}/hardware`),
                    api.request(`/tickets/${encodeURIComponent(ticketId)}/hardware/history`),
                    api.request(`/tickets/${encodeURIComponent(ticketId)}/hardware/checklist`)
                ]);
                state.specialized.hardware.details = details;
                state.specialized.hardware.history = Array.isArray(history) ? history : [];
                state.specialized.hardware.checklist = checklist;
            } else {
                const [details, logs] = await Promise.all([
                    api.request(`/tickets/${encodeURIComponent(ticketId)}/software`),
                    api.request(`/tickets/${encodeURIComponent(ticketId)}/software/logs`)
                ]);
                state.specialized.software.details = details;
                state.specialized.software.logs = Array.isArray(logs) ? logs : [];
            }
        } catch (error) {
            console.error(`Erro ao carregar dados de ${type.toLowerCase()}:`, error);
            state.specialized.error = error.message || 'Não foi possível carregar os dados especializados.';
        } finally {
            state.specialized.loading = false;
            if (state.ticket) renderTicket();
        }
    }

    function renderTicket() {
        const ticket = state.ticket;
        const code = getTicketCode(ticket);
        document.title = `${code} — Speed Desk`;
        root.replaceChildren();
        root.setAttribute('aria-busy', 'false');

        const layout = document.createElement('div');
        layout.className = 'ticket-detail-layout';
        const main = document.createElement('article');
        main.className = 'page-card ticket-detail-main';
        const hero = document.createElement('header');
        hero.className = 'ticket-detail-hero';
        const eyebrow = document.createElement('div');
        eyebrow.className = 'ticket-detail-eyebrow';
        const codeElement = document.createElement('span');
        codeElement.className = 'ticket-detail-code';
        codeElement.textContent = code;
        eyebrow.append(
            codeElement,
            createBadge(STATUS_LABELS[ticket.status] || ticket.status || 'Recebido', `badge-status ${statusClass(ticket.status)}`)
        );
        const title = document.createElement('h1');
        title.textContent = ticket.titulo || 'Chamado sem título';
        const badges = document.createElement('div');
        badges.className = 'ticket-detail-badges';
        badges.append(
            createBadge(TYPE_LABELS[ticket.ticketType] || ticket.ticketType || 'Geral', 'badge-ticket-type'),
            createBadge(PRIORITY_LABELS[ticket.prioridade] || ticket.prioridade || 'Baixa', priorityClass(ticket.prioridade)),
            createBadge(ticket.category?.name || 'Sem categoria', 'badge-category')
        );
        hero.append(eyebrow, title, badges);

        const descriptionSection = document.createElement('section');
        descriptionSection.className = 'ticket-detail-section';
        const descriptionTitle = document.createElement('h2');
        descriptionTitle.textContent = 'Descrição do problema';
        const description = document.createElement('p');
        description.className = 'ticket-detail-description';
        description.textContent = ticket.descricao || 'Nenhuma descrição foi informada.';
        descriptionSection.append(descriptionTitle, description);

        const datesSection = document.createElement('section');
        datesSection.className = 'ticket-detail-section';
        const datesTitle = document.createElement('h2');
        datesTitle.textContent = 'Datas do atendimento';
        const dates = document.createElement('dl');
        dates.className = 'ticket-detail-dates';
        dates.append(
            createDetailField('Criado em', formatDate(ticket.dataCriacao)),
            createDetailField('Atualizado em', formatDate(ticket.dataAtualizacao)),
            createDetailField('Vencimento do SLA', formatDate(ticket.dataVencimento)),
            createDetailField('Resolvido em', formatDate(ticket.resolvedAt)),
            createDetailField('Fechado em', formatDate(ticket.closedAt)),
            createDetailField('Versão do registro', String(ticket.version ?? 'Não informada'))
        );
        datesSection.append(datesTitle, dates);

        const identificationSection = document.createElement('section');
        identificationSection.className = 'ticket-detail-section ticket-id-section';
        const idTitle = document.createElement('h2');
        idTitle.textContent = 'Identificação técnica';
        const idValue = document.createElement('code');
        idValue.textContent = ticket.id;
        identificationSection.append(idTitle, idValue);
        main.append(hero, descriptionSection);
        const specializedSection = createSpecializedSection();
        if (specializedSection) main.appendChild(specializedSection);
        main.append(datesSection, identificationSection, createCommentsSection());

        const aside = document.createElement('aside');
        aside.className = 'ticket-detail-aside';
        aside.setAttribute('aria-label', 'Informações e ações relacionadas ao chamado');
        const slaInfo = getSlaInfo(ticket);
        const slaCard = document.createElement('article');
        slaCard.className = `detail-sla-card ${slaInfo.tone}`;
        const slaLabel = document.createElement('span');
        slaLabel.className = 'detail-sla-label';
        slaLabel.textContent = 'SLA calculado pelo servidor';
        const slaTitle = document.createElement('strong');
        slaTitle.textContent = slaInfo.title;
        const slaDescription = document.createElement('p');
        slaDescription.textContent = slaInfo.description;
        slaCard.append(slaLabel, slaTitle, slaDescription);

        const clientCard = createInfoCard(
            'Solicitante',
            '<path d="M20 21a8 8 0 0 0-16 0"></path><circle cx="12" cy="7" r="4"></circle>',
            [
                { label: 'Nome', value: ticket.cliente?.name },
                { label: 'E-mail', value: ticket.cliente?.email },
                { label: 'Organização', value: ticket.cliente?.organization?.name || 'Sem organização' }
            ]
        );
        const technicianCard = createInfoCard(
            'Responsável técnico',
            '<path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="8.5" cy="7" r="4"></circle><path d="M20 8v6M23 11h-6"></path>',
            ticket.tecnico
                ? [{ label: 'Nome', value: ticket.tecnico.name }, { label: 'E-mail', value: ticket.tecnico.email }]
                : [{ label: 'Situação', value: 'Sem técnico atribuído' }]
        );
        const assetCard = createInfoCard(
            'Equipamento relacionado',
            '<rect x="3" y="5" width="18" height="13" rx="2"></rect><path d="M8 21h8M12 18v3"></path>',
            ticket.asset
                ? [
                    { label: 'Nome', value: ticket.asset.nome },
                    { label: 'Tipo', value: ticket.asset.tipo },
                    { label: 'Número de série', value: ticket.asset.numeroSerie }
                ]
                : [{ label: 'Situação', value: 'Nenhum equipamento vinculado' }]
        );
        aside.append(slaCard, createWorkflowCard(ticket), clientCard, technicianCard, assetCard);
        layout.append(main, aside);
        root.appendChild(layout);
    }

    async function loadComments() {
        state.commentsLoading = true;
        state.commentsError = '';
        try {
            const response = await api.request(`/tickets/${encodeURIComponent(ticketId)}/comments`);
            const comments = Array.isArray(response) ? response : [];
            state.comments = role === 'CLIENTE'
                ? comments.filter(comment => !comment.internal)
                : comments;
        } catch (error) {
            console.error('Erro ao carregar comentários:', error);
            state.comments = [];
            state.commentsError = error.message || 'Não foi possível carregar as mensagens.';
        } finally {
            state.commentsLoading = false;
            if (state.ticket) renderTicket();
        }
    }

    async function submitComment(event, internal, textarea, submitButton) {
        event.preventDefault();
        const content = textarea.value.trim();
        if (!content || submitButton.disabled) {
            textarea.focus();
            return;
        }
        if (internal && !isTeamMember) return;
        submitButton.disabled = true;
        const originalLabel = submitButton.textContent;
        submitButton.textContent = 'Enviando...';
        try {
            const created = await api.request(`/tickets/${encodeURIComponent(ticketId)}/comments`, {
                method: 'POST',
                body: JSON.stringify({ content, internal })
            });
            state.comments = [...state.comments, created];
            showToast(internal ? 'Nota interna adicionada.' : 'Comentário enviado.', 'success');
            renderTicket();
            const nextTextarea = document.getElementById(internal ? 'internalCommentContent' : 'publicCommentContent');
            nextTextarea?.focus();
        } catch (error) {
            showToast(error.message || 'Não foi possível enviar a mensagem.', 'error');
            submitButton.disabled = false;
            submitButton.textContent = originalLabel;
        }
    }

    function openActionModal(action, trigger) {
        state.pendingAction = action;
        state.lastFocusedElement = trigger;
        actionTitle.textContent = action.title;
        actionDescription.textContent = action.description;
        actionConfirm.textContent = action.confirmLabel;
        actionConfirm.className = `btn ${action.kind === 'close' ? 'btn-success' : 'btn-primary'}`;
        pauseField.hidden = action.kind !== 'pause';
        pauseReason.required = action.kind === 'pause';
        pauseReason.value = '';
        setFeedback('');
        actionModal.hidden = false;
        actionModal.removeAttribute('inert');
        actionModal.setAttribute('aria-hidden', 'false');
        window.requestAnimationFrame(() => {
            actionModal.classList.add('active');
            (action.kind === 'pause' ? pauseReason : actionConfirm).focus();
        });
    }

    function closeActionModal(force = false) {
        if (actionConfirm.disabled && !force) return;
        actionModal.classList.remove('active');
        actionModal.setAttribute('aria-hidden', 'true');
        actionModal.setAttribute('inert', '');
        actionModal.hidden = true;
        state.pendingAction = null;
        setFeedback('');
        const focusTarget = state.lastFocusedElement;
        state.lastFocusedElement = null;
        if (focusTarget instanceof HTMLElement && document.contains(focusTarget)) focusTarget.focus();
    }

    async function executePendingAction() {
        const action = state.pendingAction;
        if (!action || actionConfirm.disabled) return;
        const reason = pauseReason.value.trim();
        if (action.kind === 'pause' && (!reason || reason.length > 500)) {
            setFeedback('Informe um motivo com no máximo 500 caracteres.', 'error');
            pauseReason.focus();
            return;
        }

        const requestByKind = {
            status: () => api.request(`/tickets/${ticketId}/status`, {
                method: 'PATCH',
                body: JSON.stringify({ status: action.status })
            }),
            assign: () => api.request(`/tickets/${ticketId}/assumir/${action.technicianId}`, {
                method: 'PATCH'
            }),
            close: () => api.request(`/tickets/${ticketId}/close`, { method: 'POST' }),
            reopen: () => api.request(`/tickets/${ticketId}/reopen`, { method: 'POST' }),
            pause: () => api.request(`/tickets/${ticketId}/sla/pause`, {
                method: 'POST',
                body: JSON.stringify({ reason })
            }),
            resume: () => api.request(`/tickets/${ticketId}/sla/resume`, { method: 'POST' })
        };
        const execute = requestByKind[action.kind];
        if (!execute) return;

        actionConfirm.disabled = true;
        const defaultLabel = actionConfirm.textContent;
        actionConfirm.textContent = 'Processando...';
        try {
            const updated = await execute();
            if (updated?.id) state.ticket = updated;
            else state.ticket = await api.request(`/tickets/${encodeURIComponent(ticketId)}`);
            if (action.kind === 'assign') state.selectedTechnicianId = '';
            actionConfirm.disabled = false;
            actionConfirm.textContent = defaultLabel;
            closeActionModal();
            renderTicket();
            if (
                role === 'GERENTE'
                && ticketCanBeAssigned(state.ticket)
                && !state.techniciansLoaded
                && !state.techniciansLoading
            ) {
                loadTechnicians();
            }
            showToast(action.successMessage || 'Chamado atualizado com sucesso.', 'success');
        } catch (error) {
            if (error.status === 409) {
                setFeedback('O chamado foi atualizado por outra operação. Recarregue os dados e tente novamente.', 'error');
            } else {
                setFeedback(error.message || 'Não foi possível concluir a ação.', 'error');
            }
        } finally {
            actionConfirm.disabled = false;
            actionConfirm.textContent = defaultLabel;
        }
    }

    function trapModalFocus(event) {
        if (event.key !== 'Tab' || !actionModal.classList.contains('active')) return;
        const focusable = [...actionModal.querySelectorAll('button:not(:disabled), textarea:not(:disabled), input:not(:disabled), select:not(:disabled), [href]')]
            .filter(element => !element.hidden && element.offsetParent !== null);
        if (!focusable.length) return;
        const first = focusable[0];
        const last = focusable.at(-1);
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    }

    document.getElementById('closeTicketActionModal').addEventListener('click', () => closeActionModal());
    document.getElementById('cancelTicketAction').addEventListener('click', () => closeActionModal());
    actionConfirm.addEventListener('click', executePendingAction);
    actionModal.addEventListener('click', event => {
        if (event.target === actionModal) closeActionModal();
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && actionModal.classList.contains('active')) closeActionModal();
        trapModalFocus(event);
    });

    async function loadPage() {
        if (!UUID_PATTERN.test(ticketId)) {
            renderError('Chamado não identificado', 'O endereço não contém um identificador de chamado válido.');
            return;
        }
        try {
            const ticket = await api.request(`/tickets/${encodeURIComponent(ticketId)}`);
            if (!ticket?.id) {
                renderError('Chamado indisponível', 'A API não devolveu os dados esperados para este chamado.');
                return;
            }
            state.ticket = ticket;
            state.specialized.loading = ticket.ticketType === 'HARDWARE' || ticket.ticketType === 'SOFTWARE';
            const shouldLoadTechnicians = role === 'GERENTE' && ticketCanBeAssigned(ticket);
            state.techniciansLoading = shouldLoadTechnicians;
            renderTicket();
            const loaders = [loadComments()];
            if (shouldLoadTechnicians) loaders.push(loadTechnicians());
            if (state.specialized.loading) loaders.push(loadSpecializedData());
            await Promise.allSettled(loaders);
        } catch (error) {
            console.error('Erro ao carregar detalhes do chamado:', error);
            renderError(
                error.status === 404 ? 'Chamado não encontrado' : 'Não foi possível carregar o chamado',
                error.message || 'Tente novamente em alguns instantes.'
            );
        }
    }

    const assistantLink = document.getElementById('assistantTicketLink');
    if (assistantLink && UUID_PATTERN.test(ticketId)) {
        assistantLink.href = `assistente.html?ticketId=${encodeURIComponent(ticketId)}`;
    }

    loadPage();
});
