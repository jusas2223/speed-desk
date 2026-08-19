const BASE_URL = 'http://localhost:8080/api';
const SESSION_KEY = 'speeddesk_session';

const api = {
    BASE_URL,

    setSession(sessionData) {
        // Limpar possíveis dados antigos do localStorage para evitar conflitos
        localStorage.removeItem('user');

        const expiresAt = Date.now() + Number(sessionData.expiresIn) * 1000;

        const cleanSession = {
            id: sessionData.id,
            name: sessionData.name,
            email: sessionData.email,
            role: sessionData.role,
            accessToken: sessionData.accessToken,
            tokenType: sessionData.tokenType,
            expiresAt: expiresAt
        };

        sessionStorage.setItem(SESSION_KEY, JSON.stringify(cleanSession));
    },

    getSession() {
        const data = sessionStorage.getItem(SESSION_KEY);
        if (!data) return null;
        try {
            const session = JSON.parse(data);
            const { id, name, email, role, accessToken, tokenType, expiresAt } = session;

            if (!id || !name || !email || !role || !accessToken || !tokenType || !expiresAt) {
                this.clearSession();
                return null;
            }
            if (!Number.isFinite(expiresAt) || Date.now() > expiresAt) {
                this.clearSession();
                return null;
            }
            return session;
        } catch (e) {
            this.clearSession();
            return null;
        }
    },

    clearSession() {
        sessionStorage.removeItem(SESSION_KEY);
        localStorage.removeItem('user'); // Garantir limpeza do antigo
    },

    requireAuth() {
        const session = this.getSession();
        if (!session || !session.accessToken) {
            this.clearSession();
            window.location.href = 'index.html';
            return null;
        }
        return session;
    },

    logout() {
        this.clearSession();
        window.location.href = 'index.html';
    },

    async request(endpoint, options = {}) {
        const url = `${this.BASE_URL}${endpoint}`;
        const { publicRequest, ...fetchOptions } = options;
        const headers = { ...fetchOptions.headers };

        // Adiciona Content-Type json se houver body em formato string JSON
        if (!headers['Content-Type'] && fetchOptions.body && typeof fetchOptions.body === 'string') {
            headers['Content-Type'] = 'application/json';
        }

        // Adiciona token JWT se houver sessão E não for publicRequest (ex: login)
        if (!publicRequest) {
            const session = this.getSession();
            if (session && session.accessToken) {
                headers['Authorization'] = `Bearer ${session.accessToken}`;
            }
        }

        fetchOptions.headers = headers;

        try {
            const response = await fetch(url, fetchOptions);

            if (!response.ok) {
                // Intercepta erros de autenticação globais (401 em chamadas protegidas)
                if (response.status === 401 && !publicRequest) {
                    this.clearSession();
                    window.location.href = 'index.html';
                    const err = new Error('Sessão expirada. Faça login novamente.');
                    err.status = 401;
                    throw err;
                }

                let errorMessage = `Erro HTTP! Status: ${response.status}`;
                const errorData = await response.json().catch(() => ({}));

                // Trata erros ProblemDetail do Spring
                if (errorData.detail) {
                    errorMessage = errorData.detail;
                } else if (errorData.message) {
                    errorMessage = errorData.message;
                }

                // Se houver lista ou mapa de erros (bean validation)
                if (errorData.errors) {
                    let validationErrors = [];
                    if (Array.isArray(errorData.errors)) {
                        validationErrors = errorData.errors.map(e => e.defaultMessage || e.message).filter(Boolean);
                    } else if (typeof errorData.errors === 'object') {
                        validationErrors = Object.values(errorData.errors).filter(Boolean);
                    }
                    if (validationErrors.length > 0) {
                        errorMessage += " - " + validationErrors.join(', ');
                    }
                }

                const err = new Error(errorMessage);
                err.status = response.status;
                err.data = errorData;
                throw err;
            }

            // Retorna vazio para 204 No Content
            if (response.status === 204) return {};

            // Pode haver retorno sem conteúdo, catch evita quebrar
            return await response.json().catch(() => ({}));
        } catch (error) {
            if (error.name === 'TypeError' || error.message.includes('Failed to fetch')) {
                const err = new Error('Não foi possível conectar ao servidor. Verifique sua conexão.');
                err.status = 0;
                throw err;
            }
            throw error;
        }
    },

    async login(email, password) {
        return this.request('/users/login', {
            method: 'POST',
            body: JSON.stringify({ email, password }),
            publicRequest: true
        });
    }
};

export default api;
