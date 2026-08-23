import api from './api.js';

const ROLE_LABELS = Object.freeze({
    CLIENTE: 'Cliente',
    TECNICO: 'Técnico independente'
});

const elements = {};
let profile = null;

document.addEventListener('DOMContentLoaded', () => {
    if (!api.requireAuth()) return;
    cacheElements();
    elements.profileForm.addEventListener('submit', updateProfile);
    elements.passwordForm.addEventListener('submit', changePassword);
    loadProfile();
});

function cacheElements() {
    elements.status = document.getElementById('profileStatus');
    elements.layout = document.querySelector('.profile-layout');
    elements.roleBadge = document.getElementById('profileRoleBadge');
    elements.avatar = document.getElementById('profileAvatar');
    elements.displayName = document.getElementById('profileDisplayName');
    elements.displayEmail = document.getElementById('profileDisplayEmail');
    elements.displayRole = document.getElementById('profileDisplayRole');
    elements.displayOrganization = document.getElementById('profileDisplayOrganization');
    elements.createdAt = document.getElementById('profileCreatedAt');

    elements.profileForm = document.getElementById('profileForm');
    elements.name = document.getElementById('profileName');
    elements.email = document.getElementById('profileEmail');
    elements.phone = document.getElementById('profilePhone');
    elements.profileFeedback = document.getElementById('profileFeedback');
    elements.profileSubmit = document.getElementById('profileSubmit');

    elements.passwordForm = document.getElementById('passwordForm');
    elements.currentPassword = document.getElementById('currentPassword');
    elements.newPassword = document.getElementById('newPassword');
    elements.confirmPassword = document.getElementById('confirmPassword');
    elements.passwordFeedback = document.getElementById('passwordFeedback');
    elements.passwordSubmit = document.getElementById('passwordSubmit');
}

async function loadProfile() {
    setStatus('Carregando perfil...', 'loading');
    try {
        profile = await api.request('/account/profile');
        renderProfile();
        elements.layout.hidden = false;
        setStatus('', '');
    } catch (error) {
        setStatus(error.message || 'Não foi possível carregar o perfil.', 'error');
    }
}

function renderProfile() {
    const roleLabel = ROLE_LABELS[profile.role] || profile.role;
    elements.roleBadge.textContent = roleLabel;
    elements.avatar.textContent = getInitials(profile.name);
    elements.displayName.textContent = profile.name;
    elements.displayEmail.textContent = profile.email;
    elements.displayRole.textContent = roleLabel;
    elements.displayOrganization.textContent = profile.organization?.name || 'Sem organização';
    elements.createdAt.textContent = formatDate(profile.createdAt);
    elements.name.value = profile.name;
    elements.email.value = profile.email;
    elements.phone.value = profile.phone || '';
}

async function updateProfile(event) {
    event.preventDefault();
    if (elements.profileSubmit.disabled) return;

    const payload = {
        name: elements.name.value.trim(),
        email: elements.email.value.trim(),
        phone: elements.phone.value.replace(/\D/g, '')
    };
    if (!payload.name || !payload.email || !payload.phone) {
        setFeedback(elements.profileFeedback, 'Preencha nome, e-mail e telefone.', 'error');
        return;
    }
    if (!elements.email.validity.valid) {
        setFeedback(elements.profileFeedback, 'Informe um e-mail válido.', 'error');
        return;
    }
    if (!/^\d{10,15}$/.test(payload.phone)) {
        setFeedback(
            elements.profileFeedback,
            'Informe o telefone com DDI, usando de 10 a 15 números.',
            'error'
        );
        return;
    }

    setBusy(elements.profileSubmit, true, 'Salvando...');
    setFeedback(elements.profileFeedback, '', '');
    try {
        profile = await api.request('/account/profile', {
            method: 'PUT',
            body: JSON.stringify(payload)
        });
        api.updateSessionProfile(profile);
        renderProfile();
        setFeedback(elements.profileFeedback, 'Dados pessoais atualizados.', 'success');
    } catch (error) {
        setFeedback(
            elements.profileFeedback,
            error.message || 'Não foi possível atualizar o perfil.',
            'error'
        );
    } finally {
        setBusy(elements.profileSubmit, false, 'Salvar dados pessoais');
    }
}

async function changePassword(event) {
    event.preventDefault();
    if (elements.passwordSubmit.disabled) return;

    const currentPassword = elements.currentPassword.value;
    const newPassword = elements.newPassword.value;
    const confirmPassword = elements.confirmPassword.value;
    const validation = validatePassword(currentPassword, newPassword, confirmPassword);
    if (validation) {
        setFeedback(elements.passwordFeedback, validation, 'error');
        return;
    }

    setBusy(elements.passwordSubmit, true, 'Alterando...');
    setFeedback(elements.passwordFeedback, '', '');
    try {
        const response = await api.request('/account/password/change', {
            method: 'POST',
            body: JSON.stringify({ currentPassword, newPassword })
        });
        elements.passwordForm.reset();
        setFeedback(
            elements.passwordFeedback,
            response.message || 'Senha alterada com sucesso.',
            'success'
        );
    } catch (error) {
        setFeedback(
            elements.passwordFeedback,
            error.message || 'Não foi possível alterar a senha.',
            'error'
        );
    } finally {
        setBusy(elements.passwordSubmit, false, 'Alterar minha senha');
    }
}

function validatePassword(currentPassword, newPassword, confirmPassword) {
    if (!currentPassword || !newPassword || !confirmPassword) {
        return 'Preencha os três campos de senha.';
    }
    if (newPassword.length < 8 || newPassword.length > 72) {
        return 'A nova senha deve possuir entre 8 e 72 caracteres.';
    }
    if (new TextEncoder().encode(newPassword).length > 72) {
        return 'A nova senha deve possuir no máximo 72 bytes em UTF-8.';
    }
    if (newPassword !== confirmPassword) {
        return 'A confirmação não corresponde à nova senha.';
    }
    if (currentPassword === newPassword) {
        return 'A nova senha deve ser diferente da senha atual.';
    }
    return '';
}

function setBusy(button, busy, label) {
    button.disabled = busy;
    button.textContent = label;
}

function setFeedback(element, message, tone) {
    element.textContent = message;
    element.className = `feedback ${tone || ''}`.trim();
    element.hidden = !message;
}

function setStatus(message, tone) {
    elements.status.textContent = message;
    elements.status.className = `list-status ${tone || ''}`.trim();
    elements.status.hidden = !message;
}

function getInitials(name) {
    const parts = String(name || 'Usuário').trim().split(/\s+/).filter(Boolean);
    if (!parts.length) return 'US';
    return `${parts[0][0]}${parts.length > 1 ? parts.at(-1)[0] : parts[0][1] || ''}`.toUpperCase();
}

function formatDate(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return 'Data indisponível';
    return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' }).format(date);
}
