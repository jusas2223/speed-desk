import api from './api.js';
import { bindThemeToggle, initializeTheme } from './theme.js';

document.addEventListener('DOMContentLoaded', () => {
    initializeTheme();
    bindThemeToggle(document.getElementById('toggleThemeBtn'));

    if (api.getSession()) {
        window.location.href = 'dashboard.html';
        return;
    }

    const loginForm = document.getElementById('loginForm');
    const loginError = document.getElementById('loginError');
    const submitButton = document.getElementById('submitBtn');
    const defaultButtonContent = submitButton.innerHTML;

    loginForm.addEventListener('submit', async event => {
        event.preventDefault();
        loginError.hidden = true;
        loginError.textContent = '';
        submitButton.disabled = true;
        submitButton.textContent = 'Autenticando...';

        try {
            const sessionData = await api.login(
                document.getElementById('email').value.trim(),
                document.getElementById('password').value
            );
            api.setSession(sessionData);
            window.location.href = 'dashboard.html';
        } catch (error) {
            loginError.textContent = error.message || 'Não foi possível entrar. Confira suas credenciais.';
            loginError.hidden = false;
            submitButton.disabled = false;
            submitButton.innerHTML = defaultButtonContent;
        }
    });
});
