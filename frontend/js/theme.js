const THEME_KEY = 'speeddesk-theme';

export function getPreferredTheme() {
    const savedTheme = localStorage.getItem(THEME_KEY);
    if (savedTheme === 'light' || savedTheme === 'dark') return savedTheme;
    return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
}

export function applyTheme(theme) {
    const normalizedTheme = theme === 'light' ? 'light' : 'dark';
    document.documentElement.dataset.theme = normalizedTheme;
    document.documentElement.style.colorScheme = normalizedTheme;
    return normalizedTheme;
}

export function initializeTheme() {
    return applyTheme(getPreferredTheme());
}

export function toggleTheme() {
    const currentTheme = document.documentElement.dataset.theme || initializeTheme();
    const nextTheme = currentTheme === 'dark' ? 'light' : 'dark';
    localStorage.setItem(THEME_KEY, nextTheme);
    return applyTheme(nextTheme);
}

function synchronizeThemeButton(button) {
    const theme = document.documentElement.dataset.theme || initializeTheme();
    const darkThemeActive = theme === 'dark';
    button.setAttribute('aria-pressed', String(darkThemeActive));
    button.setAttribute(
        'aria-label',
        darkThemeActive ? 'Ativar tema claro' : 'Ativar tema escuro'
    );
    button.title = darkThemeActive ? 'Ativar tema claro' : 'Ativar tema escuro';
}

export function bindThemeToggle(button) {
    if (!button || button.dataset.themeBound === 'true') return;
    button.dataset.themeBound = 'true';
    synchronizeThemeButton(button);
    button.addEventListener('click', () => {
        toggleTheme();
        synchronizeThemeButton(button);
    });
}
