import api from './api.js';

const REPORTS = Object.freeze({
    tickets: { endpoint: '/reports/tickets.csv', filename: 'speed-desk-chamados.csv' },
    assets: { endpoint: '/reports/assets.csv', filename: 'speed-desk-ativos.csv' },
    incidents: { endpoint: '/reports/incidents.csv', filename: 'speed-desk-incidentes.csv' }
});

document.addEventListener('DOMContentLoaded', () => {
    const session = api.requireAuth();
    if (!session) return;
    if (session.role !== 'GERENTE') {
        window.location.replace('dashboard.html');
        return;
    }
    const feedback = document.getElementById('reportFeedback');
    document.querySelectorAll('.report-download').forEach(button => {
        button.addEventListener('click', async () => {
            const report = REPORTS[button.dataset.report];
            if (!report) return;
            const original = button.textContent;
            button.disabled = true;
            button.textContent = 'Preparando...';
            feedback.hidden = true;
            try {
                const response = await api.download(report.endpoint);
                const blob = await response.blob();
                const url = URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.href = url;
                link.download = report.filename;
                document.body.appendChild(link);
                link.click();
                link.remove();
                URL.revokeObjectURL(url);
                feedback.className = 'feedback success report-feedback';
                feedback.textContent = `${report.filename} gerado com sucesso.`;
                feedback.hidden = false;
            } catch (error) {
                feedback.className = 'feedback error report-feedback';
                feedback.textContent = error.message;
                feedback.hidden = false;
            } finally {
                button.disabled = false;
                button.textContent = original;
            }
        });
    });
});
