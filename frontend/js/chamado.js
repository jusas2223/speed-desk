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

const CLOSED_STATUSES = new Set(['RESOLVIDO', 'FECHADO']);
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

function formatDuration(milliseconds) {
    const totalMinutes = Math.max(1, Math.ceil(Math.abs(milliseconds) / 60000));
    if (totalMinutes < 60) return `${totalMinutes} minuto${totalMinutes === 1 ? '' : 's'}`;

    const totalHours = Math.floor(totalMinutes / 60);
    if (totalHours < 24) {
        const minutes = totalMinutes % 60;
        const hourLabel = `${totalHours} hora${totalHours === 1 ? '' : 's'}`;
        return minutes > 0 ? `${hourLabel} e ${minutes} minuto${minutes === 1 ? '' : 's'}` : hourLabel;
    }

    const days = Math.floor(totalHours / 24);
    const hours = totalHours % 24;
    const dayLabel = `${days} dia${days === 1 ? '' : 's'}`;
    return hours > 0 ? `${dayLabel} e ${hours} hora${hours === 1 ? '' : 's'}` : dayLabel;
}

function getSlaInfo(ticket) {
    const deadline = parseDate(ticket.dataVencimento);
    if (!deadline) {
        return {
            title: 'Prazo não informado',
            description: 'Este chamado não possui uma data de vencimento disponível.',
            tone: 'is-neutral'
        };
    }

    if (CLOSED_STATUSES.has(ticket.status)) {
        const completedAt = parseDate(ticket.dataAtualizacao);
        if (!completedAt) {
            return {
                title: 'Chamado finalizado',
                description: `O prazo registrado era ${formatDate(ticket.dataVencimento)}.`,
                tone: 'is-complete'
            };
        }

        const difference = deadline.getTime() - completedAt.getTime();
        if (difference >= 0) {
            return {
                title: 'Concluído dentro do prazo',
                description: `Finalizado com ${formatDuration(difference)} de antecedência.`,
                tone: 'is-complete'
            };
        }
        return {
            title: 'Concluído após o prazo',
            description: `Finalizado com ${formatDuration(difference)} de atraso.`,
            tone: 'is-overdue'
        };
    }

    const difference = deadline.getTime() - Date.now();
    if (difference <= 0) {
        return {
            title: `SLA vencido há ${formatDuration(difference)}`,
            description: `O prazo terminou em ${formatDate(ticket.dataVencimento)}.`,
            tone: 'is-overdue'
        };
    }
    if (difference <= 24 * 60 * 60 * 1000) {
        return {
            title: `${formatDuration(difference)} restantes`,
            description: `Atenção: o prazo termina em ${formatDate(ticket.dataVencimento)}.`,
            tone: 'is-risk'
        };
    }
    return {
        title: `${formatDuration(difference)} restantes`,
        description: `Prazo previsto para ${formatDate(ticket.dataVencimento)}.`,
        tone: 'is-ok'
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

document.addEventListener('DOMContentLoaded', async () => {
    const session = api.requireAuth();
    if (!session) return;

    const root = document.getElementById('ticketDetail');
    const ticketId = new URLSearchParams(window.location.search).get('id') || '';

    function renderError(title, message) {
        root.replaceChildren();
        root.setAttribute('aria-busy', 'false');
        const card = document.createElement('section');
        card.className = 'page-card detail-state-card';
        const state = document.createElement('div');
        state.className = 'detail-error-state';
        const heading = document.createElement('h1');
        heading.textContent = title;
        const description = document.createElement('p');
        description.textContent = message;
        const back = document.createElement('a');
        back.className = 'btn btn-primary';
        back.href = 'chamados.html';
        back.textContent = 'Voltar aos chamados';
        state.append(heading, description, back);
        card.appendChild(state);
        root.appendChild(card);
    }

    function renderTicket(ticket) {
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
        const statusBadge = createBadge(
            STATUS_LABELS[ticket.status] || ticket.status || 'Recebido',
            `badge-status ${statusClass(ticket.status)}`
        );
        eyebrow.append(codeElement, statusBadge);

        const title = document.createElement('h1');
        title.textContent = ticket.titulo || 'Chamado sem título';

        const badges = document.createElement('div');
        badges.className = 'ticket-detail-badges';
        badges.append(
            createBadge(TYPE_LABELS[ticket.ticketType] || ticket.ticketType || 'Geral', 'badge-ticket-type'),
            createBadge(PRIORITY_LABELS[ticket.prioridade] || ticket.prioridade || 'Baixa', priorityClass(ticket.prioridade))
        );
        badges.appendChild(createBadge(
            ticket.category?.name || 'Sem categoria',
            'badge-category'
        ));
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
            createDetailField('Vencimento do SLA', formatDate(ticket.dataVencimento))
        );
        datesSection.append(datesTitle, dates);

        const identificationSection = document.createElement('section');
        identificationSection.className = 'ticket-detail-section ticket-id-section';
        const idTitle = document.createElement('h2');
        idTitle.textContent = 'Identificação técnica';
        const idValue = document.createElement('code');
        idValue.textContent = ticket.id;
        identificationSection.append(idTitle, idValue);
        main.append(hero, descriptionSection, datesSection, identificationSection);

        const aside = document.createElement('aside');
        aside.className = 'ticket-detail-aside';
        aside.setAttribute('aria-label', 'Informações relacionadas ao chamado');

        const slaInfo = getSlaInfo(ticket);
        const slaCard = document.createElement('article');
        slaCard.className = `detail-sla-card ${slaInfo.tone}`;
        const slaLabel = document.createElement('span');
        slaLabel.className = 'detail-sla-label';
        slaLabel.textContent = 'SLA do chamado';
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
                ? [
                    { label: 'Nome', value: ticket.tecnico.name },
                    { label: 'E-mail', value: ticket.tecnico.email }
                ]
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

        aside.append(slaCard, clientCard, technicianCard, assetCard);
        layout.append(main, aside);
        root.appendChild(layout);
    }

    if (!UUID_PATTERN.test(ticketId)) {
        renderError(
            'Chamado não identificado',
            'O endereço não contém um identificador de chamado válido.'
        );
        return;
    }

    try {
        const ticket = await api.request(`/tickets/${encodeURIComponent(ticketId)}`);
        if (!ticket?.id) {
            renderError('Chamado indisponível', 'A API não devolveu os dados esperados para este chamado.');
            return;
        }
        renderTicket(ticket);
    } catch (error) {
        console.error('Erro ao carregar detalhes do chamado:', error);
        renderError(
            error.status === 404 ? 'Chamado não encontrado' : 'Não foi possível carregar o chamado',
            error.message || 'Tente novamente em alguns instantes.'
        );
    }
});
