import api from './api.js';
import { bindThemeToggle, initializeTheme } from './theme.js';
import { startRealtime } from './realtime.js';

const ICONS = Object.freeze({
    panel: '<rect x="3" y="3" width="7" height="7" rx="1"></rect><rect x="14" y="3" width="7" height="7" rx="1"></rect><rect x="3" y="14" width="7" height="7" rx="1"></rect><rect x="14" y="14" width="7" height="7" rx="1"></rect>',
    ticket: '<path d="M3 7a2 2 0 0 0 2-2h14a2 2 0 0 0 2 2v3a2 2 0 0 0 0 4v3a2 2 0 0 0-2 2H5a2 2 0 0 0-2-2v-3a2 2 0 0 0 0-4z"></path><path d="M13 5v2M13 17v2M13 10v4"></path>',
    users: '<path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="8.5" cy="7" r="4"></circle><path d="M20 8v6M23 11h-6"></path>',
    asset: '<rect x="3" y="5" width="18" height="13" rx="2"></rect><path d="M8 21h8M12 18v3"></path>',
    incident: '<path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"></path><path d="M12 9v4M12 17h.01"></path>',
    report: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"></path>',
    settings: '<circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1A2 2 0 1 1 4.4 17l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1 1.7 1.7 0 0 0-.3-1.8l-.1-.1A2 2 0 1 1 7 4.4l.1.1a1.7 1.7 0 0 0 1.8.3h.1a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1A2 2 0 1 1 19.6 7l-.1.1a1.7 1.7 0 0 0-.3 1.8v.1a1.7 1.7 0 0 0 1.5 1h.3a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"></path>',
    bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M13.7 21a2 2 0 0 1-3.4 0"></path>',
    profile: '<path d="M20 21a8 8 0 0 0-16 0"></path><circle cx="12" cy="7" r="4"></circle>',
    bot: '<rect x="4" y="7" width="16" height="13" rx="3"></rect><path d="M12 3v4M8 12h.01M16 12h.01M9 16h6"></path>',
    logout: '<path d="M10 17l5-5-5-5M15 12H3"></path><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"></path>',
    hardware: '<rect x="4" y="4" width="16" height="16" rx="2"></rect><rect x="9" y="9" width="6" height="6"></rect>',
    software: '<path d="m8 9-4 3 4 3M16 9l4 3-4 3M14 5l-4 14"></path>',
    queue: '<path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"></path>'
});

const ROLE_LABELS = Object.freeze({
    CLIENTE: 'Cliente',
    TECNICO: 'Técnico de suporte',
    GERENTE: 'Gerente de operações'
});

const TICKET_COUNT_STORAGE_KEY = 'speeddesk-ticket-count';

const MENU_ITEMS = Object.freeze({
    CLIENTE: [
        { label: 'Painel', icon: 'panel', href: 'dashboard.html', page: 'dashboard.html' },
        {
            label: 'Meus chamados',
            icon: 'ticket',
            href: 'chamados.html',
            pages: ['chamados.html', 'chamado.html'],
            count: true
        },
        {
            label: 'Meus equipamentos',
            icon: 'asset',
            href: 'assets.html',
            pages: ['assets.html', 'ativo.html']
        }
    ],
    TECNICO: [
        { label: 'Painel', icon: 'panel', href: 'dashboard.html', page: 'dashboard.html' },
        {
            label: 'Fila de atendimento',
            icon: 'queue',
            href: 'chamados.html',
            pages: ['chamados.html', 'chamado.html'],
            count: true
        },
        {
            label: 'Ativos',
            icon: 'asset',
            href: 'assets.html',
            pages: ['assets.html', 'ativo.html']
        },
        { label: 'Hardware', icon: 'hardware', href: 'chamados.html?ticketType=HARDWARE' },
        { label: 'Software', icon: 'software', href: 'chamados.html?ticketType=SOFTWARE' },
        { label: 'Incidentes', icon: 'incident', href: 'incidentes.html', page: 'incidentes.html' }
    ],
    GERENTE: [
        { label: 'Painel', icon: 'panel', href: 'dashboard.html', page: 'dashboard.html' },
        {
            label: 'Chamados',
            icon: 'ticket',
            href: 'chamados.html',
            pages: ['chamados.html', 'chamado.html'],
            count: true
        },
        { label: 'Usuários', icon: 'users', href: 'usuarios.html', page: 'usuarios.html' },
        {
            label: 'Ativos',
            icon: 'asset',
            href: 'assets.html',
            pages: ['assets.html', 'ativo.html']
        },
        { label: 'Incidentes', icon: 'incident', href: 'incidentes.html', page: 'incidentes.html' },
        { label: 'Relatórios', icon: 'report', href: 'relatorios.html', page: 'relatorios.html' },
        { label: 'Configurações', icon: 'settings', href: 'configuracoes.html', page: 'configuracoes.html' }
    ]
});

const ACCOUNT_ITEMS = Object.freeze([
    { label: 'Notificações', icon: 'bell', href: 'notificacoes.html', page: 'notificacoes.html', notifications: true },
    { label: 'Meu perfil', icon: 'profile', href: 'perfil.html', page: 'perfil.html' },
    { label: 'Assistente IA', icon: 'bot', future: true },
    { label: 'Sair', icon: 'logout', logout: true }
]);

function iconMarkup(name) {
    return `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${ICONS[name] || ''}</svg>`;
}

function getInitials(name) {
    const words = String(name || 'Usuário').trim().split(/\s+/).filter(Boolean);
    if (words.length === 0) return 'US';
    return (words[0][0] + (words.length > 1 ? words.at(-1)[0] : words[0][1] || '')).toUpperCase();
}

function createNavigationItem(item, currentPage) {
    const listItem = document.createElement('li');
    const element = item.future ? document.createElement('button') : document.createElement('a');
    element.className = 'nav-item';

    if (item.future) {
        element.type = 'button';
        element.disabled = true;
        element.setAttribute('aria-disabled', 'true');
        element.title = `${item.label} — em breve`;
    } else if (item.logout) {
        element.href = '#logout';
        element.id = 'logoutBtn';
        element.classList.add('nav-item-danger');
    } else {
        element.href = item.href;
        const isCurrentPage = item.page === currentPage || item.pages?.includes(currentPage);
        if (isCurrentPage) element.setAttribute('aria-current', 'page');
    }

    element.insertAdjacentHTML('beforeend', iconMarkup(item.icon));

    const label = document.createElement('span');
    label.className = 'nav-label';
    label.textContent = item.label;
    element.appendChild(label);

    if (item.future) {
        const badge = document.createElement('span');
        badge.className = 'nav-badge';
        badge.textContent = 'Em breve';
        element.appendChild(badge);
    }

    if (item.count) {
        const count = document.createElement('span');
        count.className = 'nav-count';
        count.dataset.ticketNavCount = '';
        count.textContent = sessionStorage.getItem(TICKET_COUNT_STORAGE_KEY) || '0';
        element.appendChild(count);
    }

    if (item.notifications) {
        const count = document.createElement('span');
        count.className = 'nav-count';
        count.dataset.notificationCount = '';
        count.hidden = true;
        element.appendChild(count);
    }

    listItem.appendChild(element);
    return listItem;
}

function buildNavigationSection(title, items, currentPage, account = false) {
    const section = document.createElement('section');
    section.className = account ? 'nav-section nav-section-account' : 'nav-section';

    const heading = document.createElement('h2');
    heading.className = 'nav-section-title';
    heading.textContent = title;

    const list = document.createElement('ul');
    list.className = 'nav-list';
    items.forEach(item => list.appendChild(createNavigationItem(item, currentPage)));

    section.append(heading, list);
    return section;
}

function renderSidebar(container, session) {
    const role = String(session.role || '').toUpperCase();
    const currentPage = window.location.pathname.split('/').pop() || 'dashboard.html';
    const menu = MENU_ITEMS[role];

    if (!menu) {
        api.logout();
        return;
    }

    container.replaceChildren();

    const brand = document.createElement('div');
    brand.className = 'sidebar-brand';
    brand.innerHTML = `
        <a class="brand-lockup" href="dashboard.html" aria-label="Speed Desk — painel">
            <img class="brand-mark" src="assets/logo.svg" alt="">
            <span class="brand-copy">
                <span class="brand-name">Speed <span class="brand-name-accent">Desk</span></span>
                <span class="brand-tagline">Suporte em movimento.</span>
            </span>
        </a>
    `;

    const userCard = document.createElement('div');
    userCard.className = 'sidebar-user-card';
    userCard.innerHTML = `
        <span class="user-avatar" data-session-avatar></span>
        <span class="user-card-copy">
            <span class="user-card-name" data-session-name></span>
            <span class="user-card-role" data-session-role></span>
        </span>
    `;
    userCard.querySelector('[data-session-avatar]').textContent = getInitials(session.name);
    userCard.querySelector('[data-session-name]').textContent = session.name;
    userCard.querySelector('[data-session-role]').textContent = ROLE_LABELS[role];

    const nav = document.createElement('nav');
    nav.className = 'sidebar-nav';
    nav.setAttribute('aria-label', 'Navegação principal');
    nav.append(
        buildNavigationSection('Menu principal', menu, currentPage),
        buildNavigationSection('Área de conta', ACCOUNT_ITEMS, currentPage, true)
    );

    container.append(brand, userCard, nav);
}

function bindMobileNavigation(container) {
    const button = document.getElementById('mobileMenuBtn');
    if (!button) return;

    const overlay = document.createElement('div');
    overlay.className = 'sidebar-overlay';
    document.body.appendChild(overlay);

    const setOpen = open => {
        container.classList.toggle('is-open', open);
        overlay.classList.toggle('is-visible', open);
        button.setAttribute('aria-expanded', String(open));
        button.setAttribute('aria-label', open ? 'Fechar menu' : 'Abrir menu');
        document.body.style.overflow = open ? 'hidden' : '';
    };

    button.addEventListener('click', () => setOpen(!container.classList.contains('is-open')));
    overlay.addEventListener('click', () => setOpen(false));
    container.addEventListener('click', event => {
        if (event.target.closest('a')) setOpen(false);
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape') setOpen(false);
    });
}

function populateTopbar(session) {
    document.querySelectorAll('[data-topbar-avatar]').forEach(element => {
        element.textContent = getInitials(session.name);
    });
    document.querySelectorAll('[data-topbar-name]').forEach(element => {
        element.textContent = session.name;
    });
    document.querySelectorAll('[data-session-avatar]').forEach(element => {
        element.textContent = getInitials(session.name);
    });
    document.querySelectorAll('[data-session-name]').forEach(element => {
        element.textContent = session.name;
    });
}

window.addEventListener('speeddesk:session-updated', event => {
    if (event.detail) populateTopbar(event.detail);
});

export function updateTicketNavigationCount(value) {
    sessionStorage.setItem(TICKET_COUNT_STORAGE_KEY, String(value));
    document.querySelectorAll('[data-ticket-nav-count]').forEach(element => {
        element.textContent = String(value);
    });
}

function renderNotificationCount(value) {
    const count = Math.max(0, Number(value) || 0);
    document.querySelectorAll('[data-notification-count]').forEach(element => {
        element.textContent = count > 99 ? '99+' : String(count);
        element.hidden = count === 0;
    });
    document.querySelectorAll('.notification-button').forEach(button => {
        button.classList.toggle('has-notifications', count > 0);
        button.setAttribute('aria-label', count > 0
            ? `${count} notificação(ões) não lida(s)`
            : 'Notificações');
    });
}

async function initializeNotifications() {
    try {
        const summary = await api.request('/notifications/summary');
        renderNotificationCount(summary.unreadCount);
    } catch (error) {
        console.warn('Resumo de notificações indisponível:', error.message);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    initializeTheme();

    const session = api.requireAuth();
    if (!session) return;

    const sidebar = document.getElementById('sidebar-container');
    if (!sidebar) return;

    renderSidebar(sidebar, session);
    populateTopbar(session);
    bindThemeToggle(document.getElementById('toggleThemeBtn'));
    bindMobileNavigation(sidebar);
    startRealtime(session);
    initializeNotifications();

    document.querySelectorAll('.notification-button').forEach(button => {
        button.disabled = false;
        button.title = 'Abrir notificações';
        button.addEventListener('click', () => {
            window.location.href = 'notificacoes.html';
        });
    });

    window.addEventListener('speeddesk:realtime', event => {
        if (event.detail?.eventName === 'notification') initializeNotifications();
        if (event.detail?.eventName === 'notifications-read') renderNotificationCount(0);
    });

    const logoutButton = document.getElementById('logoutBtn');
    logoutButton?.addEventListener('click', event => {
        event.preventDefault();
        api.logout();
    });
});
