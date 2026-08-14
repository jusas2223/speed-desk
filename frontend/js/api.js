const BASE_URL = 'http://localhost:8080/api';

const api = {
    BASE_URL,
    
    /**
     * Helper base para requisições Fetch
     */
    async request(endpoint, options = {}) {
        const url = `${this.BASE_URL}${endpoint}`;
        const headers = {
            'Content-Type': 'application/json',
            ...options.headers,
        };

        try {
            const response = await fetch(url, { ...options, headers });
            
            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || `Erro HTTP! Status: ${response.status}`);
            }
            
            // Retorna empty object para 204 No Content
            if (response.status === 204) return {};
            
            return await response.json();
        } catch (error) {
            console.error(`Falha na requisição para ${endpoint}:`, error);
            throw error;
        }
    },
    
    /**
     * Autentica o usuário
     */
    async login(email, password) {
        return this.request('/auth/login', {
            method: 'POST',
            body: JSON.stringify({ email, password })
        });
    }
};

export default api;
