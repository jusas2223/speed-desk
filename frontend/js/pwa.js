let installPrompt = null;

function ensureMetadata() {
    if (!document.querySelector('link[rel="manifest"]')) {
        const manifest = document.createElement('link');
        manifest.rel = 'manifest';
        manifest.href = 'manifest.webmanifest';
        document.head.appendChild(manifest);
    }
    if (!document.querySelector('meta[name="theme-color"]')) {
        const theme = document.createElement('meta');
        theme.name = 'theme-color';
        theme.content = '#7c3aed';
        document.head.appendChild(theme);
    }
}

function showInstallButton() {
    if (document.getElementById('pwaInstallButton') || !installPrompt) return;
    const button = document.createElement('button');
    button.id = 'pwaInstallButton';
    button.className = 'pwa-install-button';
    button.type = 'button';
    button.textContent = 'Instalar Speed Desk';
    button.addEventListener('click', async () => {
        if (!installPrompt) return;
        await installPrompt.prompt();
        await installPrompt.userChoice;
        installPrompt = null;
        button.remove();
    });
    document.body.appendChild(button);
}

ensureMetadata();

if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('./sw.js').catch(error => {
            console.warn('PWA indisponível:', error.message);
        });
    });
}

window.addEventListener('beforeinstallprompt', event => {
    event.preventDefault();
    installPrompt = event;
    showInstallButton();
});

window.addEventListener('appinstalled', () => {
    installPrompt = null;
    document.getElementById('pwaInstallButton')?.remove();
});
