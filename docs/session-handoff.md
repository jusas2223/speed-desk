# Ponto de retomada do Speed Desk

Atualizado em 22 de agosto de 2026.

## Estado consolidado

- O escopo funcional aprovado está concluído.
- Backend exclusivamente Java 26 e Spring Boot 4.1.
- Frontend exclusivamente HTML, CSS e JavaScript sem framework.
- Banco oficial PostgreSQL hospedado no Supabase; desenvolvimento offline pelo perfil `localdev` com H2.
- Branch atual: `main`.
- Último commit funcional: `cb12ce2 feat: add PWA and intelligent assistance`.
- O repositório local estava nove commits à frente de `origin/main` antes deste documento.
- Nenhum push foi realizado pelo Codex.

## Entregas concluídas na sessão

- Fluxos de ativos, hardware, software, chamados, comentários e SLA.
- Usuários, organizações, categorias e configurações administrativas.
- Incidentes, notificações internas, SSE e exportações CSV.
- Idempotência persistida, rate limiting e OpenAPI/Swagger.
- PWA instalável com cache somente do shell estático.
- Triagem de chamados e assistente inteligente pelo backend Java.
- Integração Gemini opcional por `SPEEDDESK_AI_API_KEY`, com fallback local identificado quando não existe chave ou o provedor está indisponível.
- Design final em roxo, temas claro/escuro e identidade visual baseada no protótipo aprovado.

## Validação registrada

- Backend: 191 testes, 0 falhas, 0 erros e 0 ignorados.
- Frontend: 20 arquivos JavaScript aprovados por verificação de sintaxe.
- Manifesto PWA válido e shell offline verificado em navegador.
- Login, triagem e assistente validados em navegador no perfil `localdev`.
- `git diff --check` aprovado e working tree limpo ao concluir o código funcional.

## Supabase

- Projeto: `vgjxvkfvessfpjsyhxul`.
- 18 tabelas públicas alinhadas ao schema atual.
- RLS habilitado em todas as tabelas públicas.
- Nenhum alerta do advisor de segurança.
- Quatro migrations controladas registradas:
  - `grant_speeddesk_backend_access`;
  - `upgrade_legacy_schema_to_current`;
  - `add_incidents_notifications_and_realtime_support`;
  - `add_persisted_idempotency_records`.
- Os avisos informativos de desempenho indicam índices ainda sem uso por causa do baixo volume de dados; eles foram preservados para as consultas previstas.

## Teste temporário pela internet

- O frontend e o backend foram unidos somente em memória/artefatos de build para teste por um túnel HTTPS temporário.
- O login público e uma consulta autenticada de chamados retornaram HTTP 200.
- As alterações auxiliares usadas para o túnel foram revertidas; não fazem parte do repositório.
- Túnel e servidores temporários devem permanecer desligados entre sessões.
- Quick Tunnels não garantem SSE; o teste definitivo de tempo real deve ser local ou em uma hospedagem apropriada.

## Próxima retomada

1. Executar `git status` e confirmar a branch `main`.
2. Iniciar o ambiente local com `./start-local.ps1`.
3. Servir `frontend/` na porta 5500 conforme o README.
4. Fazer uma rodada manual de aceite por perfil: cliente, técnico e gerente.
5. Corrigir somente problemas encontrados no aceite.
6. Quando aprovado, executar `git push` para enviar os commits locais.
7. Manter decisões de hospedagem e ambientes abertas até uma escolha explícita.

Este documento é o ponto de entrada da próxima sessão. O escopo detalhado permanece em `docs/product-roadmap.md`, o schema em `docs/schema.sql` e as instruções operacionais no `README.md`.
