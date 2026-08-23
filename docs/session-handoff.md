# Ponto de retomada do Speed Desk

Atualizado em 23 de agosto de 2026.

## Estado consolidado

O pivot para marketplace de assistência técnica está implementado, auditado e validado. O domínio opera exclusivamente com `CLIENTE` e `TECNICO`, usando Spring Boot 4.1.1/Java 26 no backend, JavaScript Vanilla no frontend, H2 persistente em `localdev` e PostgreSQL/Supabase no ambiente remoto.

## Entrega funcional

- fila livre de chamados novos para qualquer técnico;
- aceite concorrente com um único vencedor e isolamento dos chamados atribuídos;
- telefone, e-mail, organização e WhatsApp protegidos até a atribuição;
- chat público entre cliente e responsável e notas internas do técnico;
- cobrança com `valorFinal`, `pagamentoRealizado` e `AGUARDANDO_PAGAMENTO`;
- bloqueio backend e banner frontend para cliente com pagamento pendente;
- confirmação do recebimento pelo técnico e fechamento pelo cliente;
- telas e permissões administrativas removidas;
- segunda conta técnica no seeder local para testes de concorrência.

## Correções da auditoria

- Spring Boot atualizado de 4.1.0 para 4.1.1;
- OpenAPI/Swagger desabilitado por padrão fora de `localdev` e testes;
- Service Worker impedido de interceptar ou armazenar qualquer rota `/api/`;
- leitura de ativos e histórico técnico limitada ao contexto de chamados legíveis;
- lista de chamados de um ativo filtrada pela mesma autorização individual;
- comentários e históricos de manutenção com desempate determinístico por sequência;
- configuração do servidor estático preserva query strings e adiciona cabeçalhos básicos de segurança;
- mensagem incorreta de “outro técnico” corrigida na cobrança do responsável;
- testes de autenticação, rotas administrativas legadas, CORS, privacidade e marketplace ampliados.

## Validação registrada

- backend: **126 testes, 0 falhas, 0 erros e 0 ignorados** com Java 26;
- frontend: 17 arquivos aprovados por `node --check`;
- 11 páginas HTML verificadas sem referências locais quebradas;
- `frontend/serve.json` válido e `git diff --check` aprovado;
- busca por padrões de chave privada e tokens conhecidos sem segredos reais;
- 142 dependências Maven resolvidas consultadas no OSV, sem vulnerabilidades reportadas;
- OWASP Dependency-Check não obteve a base NVD porque a execução sem chave de API foi recusada; não foi tratado como resultado de segurança;
- aceite local completo com cliente e dois técnicos: corrida de aceite, telefone, WhatsApp, chat, cobrança decimal, bloqueio, confirmação, fechamento e nova criação;
- inspeção visual no navegador confirmou dashboard do cliente e detalhe do técnico.

## Supabase

Projeto: `vgjxvkfvessfpjsyhxul`.

Migrations remotas mais recentes:

- `20260822235346 marketplace_pivot`;
- `20260823205606 security_hardening`;
- `20260823210254 deterministic_hardware_history`.

Verificação final:

- zero privilégios de tabelas ou sequências para `anon` e `authenticated`;
- usuário JDBC `speeddesk_app` com concessões explícitas;
- três constraints de consistência financeira presentes;
- duas colunas identity para ordenação determinística;
- zero registros financeiros inconsistentes;
- advisor de segurança sem alertas;
- advisor de desempenho somente com índices ainda não usados, esperado antes de tráfego real.

## Próxima retomada

1. Executar `git status` e `git log -1` para confirmar o ponto recebido.
2. Não reabrir itens rejeitados em `docs/product-roadmap.md` sem pedido explícito.
3. A próxima decisão de produto normal é hospedagem/observabilidade ou uma evolução futura aprovada.
4. Para desenvolvimento local, usar `./start-local.ps1` e servir o frontend com o comando documentado no README.

Consulte `docs/product-roadmap.md` para o escopo, `docs/backend-security.md` para as fronteiras de autorização e `docs/schema.sql` para o banco de referência.
