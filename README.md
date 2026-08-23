# Speed Desk

Speed Desk é um marketplace de assistência técnica que conecta clientes a técnicos independentes. Clientes registram equipamentos e abrem chamados; qualquer técnico pode consultar a fila de chamados novos, assumir um atendimento e combinar a execução presencial ou remota pelo chat interno ou WhatsApp.

O navegador se comunica somente com a API Spring. O PostgreSQL/Supabase nunca é acessado diretamente pelo frontend.

## Estado atual

O pivot de helpdesk corporativo para marketplace está implementado. O sistema opera exclusivamente com os perfis `CLIENTE` e `TECNICO` e inclui o ciclo de cobrança, confirmação de recebimento e bloqueio de novos chamados enquanto existir pagamento pendente.

## Stack

- Java 26, Spring Boot 4.1.1, Maven Wrapper e JPA/Hibernate;
- HTML, CSS e JavaScript Vanilla com ES Modules;
- PostgreSQL hospedado no Supabase no ambiente remoto;
- H2 persistente no perfil `localdev`;
- JWT HMAC SHA-256, BCrypt, rate limiting e idempotência persistida;
- OpenAPI/Swagger, SSE, PWA e integração opcional com Gemini.

## Estrutura

```text
backend/                  API Spring Boot
frontend/                 aplicação web estática
docs/schema.sql           referência do schema PostgreSQL
docs/product-roadmap.md   fonte de verdade do produto
docs/backend-security.md  autorização e cuidados operacionais
docs/session-handoff.md   ponto de retomada da próxima sessão
supabase/migrations/      migrations controladas do banco remoto
start-local.ps1           inicialização do backend com H2
```

## Perfis e regras do marketplace

| Perfil | Capacidades principais |
|---|---|
| `CLIENTE` | Mantém o próprio perfil e telefone, administra os próprios ativos, abre e acompanha os próprios chamados, conversa com o técnico, consulta o valor cobrado, fecha ou reabre o atendimento. Não pode abrir novo chamado se houver pagamento pendente. |
| `TECNICO` | Consulta chamados novos sem responsável e os próprios atendimentos, assume um chamado em nome próprio, acessa telefone/WhatsApp e chat somente após o aceite, executa o fluxo técnico, informa o valor final e confirma o recebimento. |

Não existe perfil administrativo na aplicação. Rotas legadas de usuários, organizações, relatórios e alteração de políticas permanecem bloqueadas pela configuração de segurança e não possuem telas no frontend.

## Fluxo de atendimento e pagamento

1. O cliente cria um chamado em `RECEBIDO`.
2. O chamado aparece na fila de todos os técnicos sem revelar telefone, e-mail ou organização do cliente.
3. Um técnico assume o chamado; ele passa para `EM_ATENDIMENTO` e libera os dados de contato, o link `wa.me` e o chat entre as duas partes.
4. Ao concluir o serviço, o técnico informa `valorFinal`; o status passa para `AGUARDANDO_PAGAMENTO` e `pagamentoRealizado` permanece `false`.
5. Enquanto a pendência existir, o backend recusa `POST /api/tickets` para esse cliente e o dashboard desabilita **Novo chamado**.
6. Após receber diretamente do cliente, o técnico confirma o pagamento; `pagamentoRealizado` passa para `true` e o chamado para `RESOLVIDO`.
7. O cliente pode fechar o chamado ou reabri-lo, o que limpa os dados da cobrança anterior.

`valor_final` usa `NUMERIC(12,2)` no banco. Não existe gateway de pagamento ou custódia de valores nesta etapa; a confirmação registra o acerto feito diretamente entre cliente e técnico.

## Endpoints principais

Durante o desenvolvimento local, todos usam o prefixo `http://localhost:8080/api`.

| Método | Rota | Regra |
|---|---|---|
| `POST` | `/users/login` | Público. Autentica e devolve JWT. |
| `GET`, `PUT` | `/account/profile` | Perfil atual; inclui telefone com DDI. |
| `POST` | `/account/password/change` | Troca autenticada da própria senha. |
| `GET` | `/tickets` | Cliente vê os próprios; técnico vê novos sem responsável e os próprios. |
| `GET` | `/tickets/payment-pending` | Cliente consulta se está bloqueado por pendência. |
| `POST` | `/tickets` | Somente cliente sem pagamento pendente. |
| `GET` | `/tickets/{ticketId}` | Respeita propriedade, fila livre e atribuição. |
| `PATCH` | `/tickets/{ticketId}/assumir/{tecnicoId}` | Técnico assume somente em nome próprio. |
| `PATCH` | `/tickets/{ticketId}/status` | Técnico atribuído executa transições operacionais; não resolve diretamente. |
| `POST` | `/tickets/{ticketId}/finalize` | Técnico atribuído informa `{ "valorFinal": 150.00 }` e inicia a cobrança. |
| `POST` | `/tickets/{ticketId}/payment/confirm` | Técnico atribuído confirma o recebimento e resolve. |
| `POST` | `/tickets/{ticketId}/close` | Cliente proprietário fecha após pagamento confirmado. |
| `POST` | `/tickets/{ticketId}/reopen` | Cliente proprietário reabre e limpa a cobrança. |
| `GET`, `POST` | `/tickets/{ticketId}/comments` | Chat liberado ao cliente e ao técnico atribuído. |
| `POST`, `GET`, `PUT` | `/assets...` | Cliente cria/edita os próprios; técnico tem leitura contextual. |
| `GET`, `PUT`, `POST` | `/tickets/{id}/hardware...` | Consulta autorizada e operações pelo técnico atribuído. |
| `GET`, `PUT`, `POST` | `/tickets/{id}/software...` | Contexto do cliente/técnico e logs pelo técnico atribuído. |
| `GET`, `POST`, `PUT` | `/incidents...` | Operação técnica. |
| `GET`, `PATCH` | `/notifications...` | Notificações privadas do usuário autenticado. |
| `GET` | `/realtime/stream` | Eventos SSE autenticados. |

## Executar localmente

Pré-requisitos: JDK 26 e Node.js.

No primeiro PowerShell, na raiz do projeto:

```powershell
.\start-local.ps1
```

O script seleciona o perfil `localdev`, usa o H2 persistente em `.speeddesk-local/`, configura CORS para a porta 5500 e inicia a API em `http://localhost:8080`.

Em outro PowerShell:

```powershell
npx --yes serve frontend --listen 5500 --config serve.json
```

Acesse [http://localhost:5500/](http://localhost:5500/).

### Contas locais

| Perfil | E-mail | Senha |
|---|---|---|
| `CLIENTE` | `cliente@speeddesk.local` | `SpeedDesk@123` |
| `TECNICO` | `tecnico@speeddesk.local` | `SpeedDesk@123` |
| `TECNICO` | `tecnico2@speeddesk.local` | `SpeedDesk@123` |

O seeder também preenche telefones fictícios com DDI; a segunda conta técnica permite validar concorrência, isolamento entre profissionais e liberação do WhatsApp.

## Ambiente remoto

O perfil padrão usa PostgreSQL/Supabase por JDBC. Configure segredos fora do repositório:

| Variável | Finalidade |
|---|---|
| `SPEEDDESK_DB_URL` | URL JDBC PostgreSQL com SSL obrigatório. |
| `SPEEDDESK_DB_USERNAME` | Usuário restrito do backend. |
| `SPEEDDESK_DB_PASSWORD` | Senha do banco. |
| `SPEEDDESK_JWT_SECRET` | Segredo HMAC com ao menos 32 bytes. |
| `SPEEDDESK_JWT_EXPIRATION_SECONDS` | Validade do JWT; padrão `3600`. |
| `SPEEDDESK_CORS_ALLOWED_ORIGINS` | Origens web permitidas, separadas por vírgula. |
| `SPEEDDESK_PASSWORD_RESET_EXPIRATION_MINUTES` | Validade interna do token; padrão `30`. |
| `SPEEDDESK_RATE_LIMIT_ENABLED` | Ativa rate limiting; padrão `true`. |
| `SPEEDDESK_AUTHENTICATED_REQUESTS_PER_MINUTE` | Limite autenticado; padrão `180`. |
| `SPEEDDESK_PUBLIC_REQUESTS_PER_MINUTE` | Limite público; padrão `20`. |
| `SPEEDDESK_AI_ENABLED` | Habilita provedor remoto; fallback local continua disponível. |
| `SPEEDDESK_AI_API_KEY` | Chave Gemini mantida somente no backend. |
| `SPEEDDESK_AI_MODEL` | Modelo configurável; padrão `gemini-2.5-flash-lite`. |
| `SPEEDDESK_AI_BASE_URL` | Endpoint REST do provedor. |
| `SPEEDDESK_AI_TIMEOUT_SECONDS` | Timeout remoto; padrão `20`. |
| `SPEEDDESK_OPENAPI_ENABLED` | Expõe OpenAPI/Swagger; padrão `false` e habilitado automaticamente em `localdev`. |

Nunca coloque credenciais do Supabase, segredo JWT ou chave de IA no frontend ou no Git.

## Validação

Backend, dentro de `backend/` com Java 26:

```powershell
.\mvnw.cmd test
```

Frontend, na raiz:

```powershell
$files = @(Get-ChildItem frontend/js -Filter *.js -File) + (Get-Item frontend/sw.js)
$files | ForEach-Object { node --check $_.FullName }
git diff --check
```

Com a API local ativa, OpenAPI fica em [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) e Swagger UI em [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html). No perfil padrão esses endpoints permanecem desabilitados, salvo configuração explícita.

## Banco de dados

- O schema de referência está em `docs/schema.sql`.
- Toda mudança estrutural remota deve ser uma migration em `supabase/migrations/`.
- O frontend não possui credenciais e não usa a Data API do Supabase.
- O backend usa `ddl-auto=validate` no PostgreSQL e `ddl-auto=update` somente no perfil H2 `localdev`.

Consulte `docs/product-roadmap.md` antes de ampliar o escopo e `docs/session-handoff.md` para o ponto exato de retomada.
