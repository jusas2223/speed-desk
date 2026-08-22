import api from './api.js';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

document.addEventListener('DOMContentLoaded', () => {
    const session = api.requireAuth();
    if (!session) return;

    const form = document.getElementById('assistantForm');
    const input = document.getElementById('assistantMessage');
    const ticketInput = document.getElementById('assistantTicketId');
    const messages = document.getElementById('assistantMessages');
    const submit = document.getElementById('assistantSubmit');
    const source = document.getElementById('assistantSource');
    const context = document.getElementById('assistantTicketContext');
    const initialTicketId = new URLSearchParams(window.location.search).get('ticketId') || '';
    ticketInput.value = initialTicketId;

    function appendMessage(kind, text, actions = []) {
        const article = document.createElement('article');
        article.className = `assistant-message is-${kind}`;
        const heading = document.createElement('strong');
        heading.textContent = kind === 'user' ? session.name : 'Speed Desk IA';
        const paragraph = document.createElement('p');
        paragraph.textContent = text;
        article.append(heading, paragraph);
        if (actions.length) {
            const list = document.createElement('ul');
            actions.forEach(action => {
                const item = document.createElement('li');
                item.textContent = action;
                list.appendChild(item);
            });
            article.appendChild(list);
        }
        messages.appendChild(article);
        messages.scrollTop = messages.scrollHeight;
    }

    async function loadTicketContext() {
        const ticketId = ticketInput.value.trim();
        context.hidden = true;
        context.replaceChildren();
        if (!UUID_PATTERN.test(ticketId)) return;
        try {
            const ticket = await api.request(`/tickets/${encodeURIComponent(ticketId)}`);
            const title = document.createElement('strong');
            title.textContent = ticket.titulo;
            const detail = document.createElement('span');
            detail.textContent = `${ticket.ticketType || 'GERAL'} · ${ticket.status}`;
            context.append(title, detail);
            context.hidden = false;
        } catch {
            context.textContent = 'Chamado indisponível para o seu perfil.';
            context.hidden = false;
        }
    }

    ticketInput.addEventListener('change', loadTicketContext);
    if (initialTicketId) loadTicketContext();

    form.addEventListener('submit', async event => {
        event.preventDefault();
        const message = input.value.trim();
        const ticketId = ticketInput.value.trim();
        if (!message) return;
        if (ticketId && !UUID_PATTERN.test(ticketId)) {
            appendMessage('assistant', 'O UUID do chamado não possui um formato válido. Corrija ou deixe o campo vazio.');
            return;
        }

        appendMessage('user', message);
        input.value = '';
        submit.disabled = true;
        submit.textContent = 'Analisando...';
        try {
            const response = await api.request('/ai/assistant', {
                method: 'POST',
                body: JSON.stringify({ ticketId: ticketId || null, message })
            });
            appendMessage('assistant', response.answer, response.suggestedActions || []);
            const labels = { GEMINI: 'Resposta gerada pelo Gemini', LOCAL: 'Assistência local', LOCAL_FALLBACK: 'Assistência local — provedor indisponível' };
            source.textContent = `${labels[response.source] || response.source} · ${response.disclaimer}`;
        } catch (error) {
            appendMessage('assistant', error.message || 'Não foi possível gerar a sugestão.');
        } finally {
            submit.disabled = false;
            submit.textContent = 'Gerar sugestão';
            input.focus();
        }
    });

    document.getElementById('clearAssistant').addEventListener('click', () => {
        messages.replaceChildren();
        appendMessage('assistant', 'Conversa limpa. Como posso ajudar agora?');
        source.textContent = 'Modo local disponível';
    });
});
