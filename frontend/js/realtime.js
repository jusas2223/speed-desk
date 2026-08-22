import api from './api.js';

let activeController = null;

export function startRealtime(session) {
    if (!session?.accessToken || activeController) return;
    activeController = new AbortController();
    connect(session, activeController.signal);
}

async function connect(session, signal) {
    while (!signal.aborted && api.getSession()) {
        try {
            const response = await fetch(`${api.BASE_URL}/realtime/stream`, {
                headers: { Authorization: `Bearer ${session.accessToken}` },
                signal
            });
            if (response.status === 401) {
                api.logout();
                return;
            }
            if (!response.ok || !response.body) throw new Error('Stream indisponível');
            await consumeStream(response.body, signal);
        } catch (error) {
            if (signal.aborted) return;
            console.warn('Canal em tempo real reconectando:', error.message);
        }
        await delay(2500, signal);
    }
}

async function consumeStream(stream, signal) {
    const reader = stream.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    while (!signal.aborted) {
        const { value, done } = await reader.read();
        if (done) return;
        buffer += decoder.decode(value, { stream: true }).replace(/\r/g, '');
        let separator = buffer.indexOf('\n\n');
        while (separator >= 0) {
            dispatchEventBlock(buffer.slice(0, separator));
            buffer = buffer.slice(separator + 2);
            separator = buffer.indexOf('\n\n');
        }
    }
}

function dispatchEventBlock(block) {
    let eventName = 'message';
    const data = [];
    block.split('\n').forEach(line => {
        if (line.startsWith('event:')) eventName = line.slice(6).trim();
        if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
    });
    if (data.length === 0) return;
    let detail = data.join('\n');
    try { detail = JSON.parse(detail); } catch (_) { /* texto SSE válido */ }
    window.dispatchEvent(new CustomEvent('speeddesk:realtime', {
        detail: { eventName, payload: detail }
    }));
}

function delay(milliseconds, signal) {
    return new Promise(resolve => {
        const timer = window.setTimeout(resolve, milliseconds);
        signal.addEventListener('abort', () => {
            window.clearTimeout(timer);
            resolve();
        }, { once: true });
    });
}
