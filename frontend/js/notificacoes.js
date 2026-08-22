import api from './api.js';

const TYPE_LABELS = Object.freeze({
    TICKET_CREATED: 'Chamado', TICKET_ASSIGNED: 'Atribuição', TICKET_STATUS_CHANGED: 'Status',
    COMMENT_ADDED: 'Comentário', INTERNAL_NOTE_ADDED: 'Nota interna', SLA_RISK: 'SLA',
    INCIDENT_CREATED: 'Incidente', INCIDENT_UPDATED: 'Incidente', SYSTEM: 'Sistema'
});

document.addEventListener('DOMContentLoaded', () => {
    if (!api.requireAuth()) return;
    const list = document.getElementById('notificationList');
    const status = document.getElementById('notificationStatus');
    const unreadBadge = document.getElementById('unreadBadge');
    const markAll = document.getElementById('markAllRead');
    let reloadTimer = null;

    function resourceLink(notification) {
        if (notification.resourceType === 'TICKET') return `chamado.html?id=${encodeURIComponent(notification.resourceId)}`;
        if (notification.resourceType === 'INCIDENT') return 'incidentes.html';
        return '';
    }

    function formatDate(value) {
        return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
    }

    function render(notifications) {
        list.replaceChildren();
        list.setAttribute('aria-busy', 'false');
        const unread = notifications.filter(item => !item.read).length;
        unreadBadge.textContent = `${unread} não lida${unread === 1 ? '' : 's'}`;
        markAll.disabled = unread === 0;
        if (!notifications.length) {
            status.className = 'list-status empty';
            status.textContent = 'Você ainda não possui notificações.';
            return;
        }
        status.className = 'list-status ready';
        status.textContent = `${notifications.length} notificação(ões), ${unread} não lida(s).`;
        notifications.forEach(notification => {
            const article = document.createElement('article');
            article.className = `notification-entry ${notification.read ? 'is-read' : 'is-unread'}`;
            const indicator = document.createElement('span');
            indicator.className = 'notification-entry-indicator';
            const body = document.createElement('div');
            body.className = 'notification-entry-body';
            const meta = document.createElement('div');
            meta.className = 'notification-entry-meta';
            const type = document.createElement('span');
            type.textContent = TYPE_LABELS[notification.type] || 'Atualização';
            const date = document.createElement('time');
            date.dateTime = notification.createdAt;
            date.textContent = formatDate(notification.createdAt);
            meta.append(type, date);
            const title = document.createElement('h2');
            title.textContent = notification.title;
            const message = document.createElement('p');
            message.textContent = notification.message;
            body.append(meta, title, message);
            const actions = document.createElement('div');
            actions.className = 'notification-entry-actions';
            const href = resourceLink(notification);
            if (href) {
                const open = document.createElement('a');
                open.className = 'btn btn-secondary btn-compact';
                open.href = href;
                open.textContent = 'Abrir';
                open.addEventListener('click', () => markRead(notification));
                actions.appendChild(open);
            }
            if (!notification.read) {
                const read = document.createElement('button');
                read.type = 'button';
                read.className = 'btn btn-ghost btn-compact';
                read.textContent = 'Marcar como lida';
                read.addEventListener('click', async () => {
                    read.disabled = true;
                    await markRead(notification);
                    load();
                });
                actions.appendChild(read);
            }
            article.append(indicator, body, actions);
            list.appendChild(article);
        });
    }

    async function markRead(notification) {
        if (notification.read) return;
        try { await api.request(`/notifications/${notification.id}/read`, { method: 'PATCH' }); }
        catch (error) { console.warn('Não foi possível marcar a notificação:', error.message); }
    }

    async function load() {
        try {
            const notifications = await api.request('/notifications');
            render(Array.isArray(notifications) ? notifications : []);
        } catch (error) {
            status.className = 'list-status error';
            status.textContent = error.message;
        }
    }

    markAll.addEventListener('click', async () => {
        markAll.disabled = true;
        try { await api.request('/notifications/read-all', { method: 'POST' }); await load(); }
        catch (error) { status.textContent = error.message; markAll.disabled = false; }
    });
    window.addEventListener('speeddesk:realtime', event => {
        if (!['notification', 'notifications-read'].includes(event.detail?.eventName)) return;
        window.clearTimeout(reloadTimer);
        reloadTimer = window.setTimeout(load, 200);
    });
    load();
});
