# Speed Desk — instruções para o Codex

Antes de analisar ou alterar o projeto, leia integralmente:

1. `docs/session-handoff.md` — ponto exato de retomada;
2. `docs/product-roadmap.md` — fonte de verdade do escopo aprovado e rejeitado;
3. `README.md` — arquitetura, execução, endpoints e variáveis de ambiente;
4. `docs/backend-security.md` — autenticação, autorização e cuidados operacionais;
5. `docs/schema.sql` — representação de referência do PostgreSQL/Supabase.

## Stack obrigatória

- Backend: Java 26, Spring Boot 4.1.1, Maven e JPA/Hibernate.
- Frontend: HTML, CSS e JavaScript Vanilla com ES Modules.
- Banco remoto: PostgreSQL no Supabase, acessado exclusivamente pelo backend Java via JDBC.
- Desenvolvimento offline: perfil Spring `localdev` com H2 persistente.
- Não introduza frameworks frontend, Supabase direto no navegador, Python ou outra linguagem para lógica, scripts de aplicação ou migrations.

## Regras de continuidade

- Considere o código e `docs/product-roadmap.md` como fontes de verdade.
- Preserve os itens conscientemente rejeitados; não reabra funcionalidades fora do escopo sem pedido explícito.
- Antes de editar, execute `git status` e preserve alterações existentes.
- Nunca exponha ou versione credenciais, tokens, senhas JDBC, segredo JWT ou chave de IA.
- Alterações estruturais do banco devem usar migrations controladas e manter `docs/schema.sql` sincronizado.
- O frontend nunca recebe credenciais do Supabase nem acessa suas tabelas diretamente.
- Mantenha autorização estritamente por `CLIENTE` e `TECNICO`, UUIDs e contratos canônicos documentados.
- Não faça push sem solicitação explícita do usuário.

## Validação mínima

- Backend: `./mvnw.cmd test` dentro de `backend/` usando Java 26.
- Frontend: executar `node --check` em todos os arquivos `frontend/js/*.js` e em `frontend/sw.js`.
- Executar `git diff --check` antes de criar um commit.
- Para testes locais, usar `./start-local.ps1` e servir `frontend/` na porta 5500 conforme o README.

## Estado para a próxima sessão

O pivot para marketplace está implementado, auditado e validado. As migrations correspondentes estão aplicadas no Supabase. A próxima atividade normal depende de uma decisão explícita de produto, como hospedagem/observabilidade ou uma evolução listada no roadmap. Consulte sempre `docs/session-handoff.md` para o estado mais recente.
