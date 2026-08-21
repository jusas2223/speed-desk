import api from './api.js';
import { bindThemeToggle, initializeTheme } from './theme.js';

document.addEventListener('DOMContentLoaded', () => {
    initializeTheme();
    bindThemeToggle(document.getElementById('toggleThemeBtn'));

    const form = document.getElementById('resetPasswordForm');
    const token = document.getElementById('resetToken');
    const newPassword = document.getElementById('resetNewPassword');
    const confirmPassword = document.getElementById('resetConfirmPassword');
    const submit = document.getElementById('resetSubmit');
    const feedback = document.getElementById('resetFeedback');
    const queryToken = new URLSearchParams(window.location.search).get('token');
    if (queryToken) {
        token.value = queryToken;
        window.history.replaceState(null, '', window.location.pathname);
    }

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (submit.disabled) return;

        const rawToken = token.value.trim();
        const password = newPassword.value;
        const confirmation = confirmPassword.value;
        const validation = validate(rawToken, password, confirmation);
        if (validation) {
            showFeedback(validation, 'error');
            return;
        }

        submit.disabled = true;
        submit.textContent = 'Redefinindo...';
        showFeedback('', '');
        try {
            const response = await api.request('/account/password-reset/confirm', {
                method: 'POST',
                body: JSON.stringify({ token: rawToken, newPassword: password }),
                publicRequest: true
            });
            form.reset();
            form.hidden = true;
            showFeedback(
                `${response.message || 'Senha redefinida com sucesso.'} Volte ao login para entrar.`,
                'success'
            );
            document.querySelector('.auth-back-link')?.focus();
        } catch (error) {
            showFeedback(
                error.message || 'Não foi possível redefinir a senha.',
                'error'
            );
            submit.disabled = false;
            submit.textContent = 'Redefinir senha';
        }
    });

    function showFeedback(message, tone) {
        feedback.textContent = message;
        feedback.className = `login-error ${tone || ''}`.trim();
        feedback.hidden = !message;
    }
});

function validate(token, password, confirmation) {
    if (!token || !password || !confirmation) return 'Preencha todos os campos.';
    if (password.length < 8 || password.length > 72) {
        return 'A nova senha deve possuir entre 8 e 72 caracteres.';
    }
    if (new TextEncoder().encode(password).length > 72) {
        return 'A nova senha deve possuir no máximo 72 bytes em UTF-8.';
    }
    if (password !== confirmation) return 'As senhas não correspondem.';
    return '';
}
