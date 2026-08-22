const CACHE_NAME = 'speeddesk-static-v4';
const APP_SHELL = [
    './',
    './index.html',
    './dashboard.html',
    './chamados.html',
    './chamado.html',
    './assets.html',
    './ativo.html',
    './usuarios.html',
    './configuracoes.html',
    './perfil.html',
    './notificacoes.html',
    './incidentes.html',
    './relatorios.html',
    './redefinir-senha.html',
    './assistente.html',
    './manifest.webmanifest',
    './css/style.css',
    './css/assets.css',
    './assets/logo.svg',
    './assets/app-icon.svg',
    './assets/bg-velocity.svg',
    './js/api.js',
    './js/navigation.js',
    './js/theme.js',
    './js/pwa.js',
    './js/realtime.js',
    './js/login.js',
    './js/dashboard.js',
    './js/chamados.js',
    './js/chamado.js',
    './js/assets.js',
    './js/ativo.js',
    './js/usuarios.js',
    './js/configuracoes.js',
    './js/perfil.js',
    './js/notificacoes.js',
    './js/incidentes.js',
    './js/relatorios.js',
    './js/redefinir-senha.js',
    './js/assistente.js'
];

self.addEventListener('install', event => {
    event.waitUntil(caches.open(CACHE_NAME).then(cache => cache.addAll(APP_SHELL)));
    self.skipWaiting();
});

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys()
            .then(keys => Promise.all(keys
                .filter(key => key !== CACHE_NAME)
                .map(key => caches.delete(key))))
            .then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', event => {
    const request = event.request;
    if (request.method !== 'GET') return;
    const url = new URL(request.url);
    if (url.origin !== self.location.origin) return;

    if (request.mode === 'navigate') {
        event.respondWith(
            fetch(request)
                .then(response => {
                    const copy = response.clone();
                    caches.open(CACHE_NAME).then(cache => cache.put(request, copy));
                    return response;
                })
                .catch(async () => (await caches.match(request)) || caches.match('./index.html'))
        );
        return;
    }

    event.respondWith(
        caches.match(request).then(cached => {
            const update = fetch(request).then(response => {
                if (response.ok) {
                    caches.open(CACHE_NAME).then(cache => cache.put(request, response.clone()));
                }
                return response;
            });
            return cached || update;
        })
    );
});
