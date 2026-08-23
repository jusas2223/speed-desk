# Segurança e autorização do backend

Atualizado em 23 de agosto de 2026 após a revisão de segurança do marketplace.

## Fronteira de confiança

O frontend é um cliente não confiável. Identificador, role, status, valor e flags enviados pelo navegador nunca substituem a identidade extraída do JWT nem as regras do servidor. O Spring Boot é o único componente autorizado a acessar PostgreSQL/Supabase por JDBC.

Segredos JDBC, segredo JWT e chave de IA ficam exclusivamente no ambiente do backend. Não devem aparecer em HTML, JavaScript, logs, commits ou respostas da API.

## Autenticação

- `POST /api/users/login` é público e aplica rate limiting por IP.
- Senhas são comparadas com BCrypt e nunca são devolvidas por DTO.
- JWT usa HMAC SHA-256, emissor e expiração configurados.
- Cada requisição autenticada revalida existência, atividade, e-mail e role atuais da conta.
- Alteração relevante da conta invalida tokens que carreguem claims antigas.
- A recuperação por token continua disponível apenas para consumo em `/api/account/password-reset/confirm`; a emissão administrativa não faz parte da interface do marketplace.

## Perfis

| Perfil | Escopo |
|---|---|
| `CLIENTE` | Próprio perfil, ativos e chamados. Cria chamado somente para si e sem pendência de pagamento. Conversa no próprio chamado, consulta cobrança e fecha/reabre após o fluxo financeiro. |
| `TECNICO` | Fila nova sem responsável e chamados assumidos por ele. Assume apenas em nome próprio, opera somente a própria atribuição, vê o contato do cliente somente após o aceite, registra valor e confirma recebimento. |

Não há perfil administrativo. `/api/users/**` (exceto login), `/api/organizations/**` e `/api/reports/**` são negados. Criação de categorias e alteração de políticas de SLA também são negadas. Incidentes são restritos a `TECNICO`.

## Autorização de chamados

### Leitura

- cliente: somente se `ticket.cliente.id` for o identificador do JWT;
- técnico: chamado atribuído a ele;
- técnico, antes do aceite: somente chamado sem responsável em `RECEBIDO` ou `EM_TRIAGEM`;
- técnico não pode ler chamado atribuído a outro técnico.

`TicketResponseDTO` evita entidades JPA e senhas. `clientPhone`, e-mail e organização do cliente são projetados somente para o cliente proprietário ou o técnico atribuído. A projeção da fila livre mantém apenas a identidade mínima do solicitante e remove esses dados de contato.

### Criação e inadimplência

`POST /api/tickets` aceita somente `CLIENTE` criando para o próprio UUID. Antes de persistir, `TicketService` consulta se existe:

- chamado em `AGUARDANDO_PAGAMENTO`; ou
- chamado finalizado com `valor_final` preenchido e `pagamento_realizado = false`.

Se existir, retorna `403 Forbidden` com a mensagem de regularização. Essa consulta possui índice parcial em `tickets(cliente_id)` para a condição de pendência. O endpoint `GET /api/tickets/payment-pending` expõe apenas o booleano do cliente autenticado.

### Aceite e comunicação

`PATCH /api/tickets/{id}/assumir/{tecnicoId}` exige que o UUID da rota seja o mesmo do técnico autenticado, que a conta esteja ativa e que o chamado ainda esteja sem responsável em estado atribuível. A atualização usa versionamento otimista; uma corrida deve permitir somente um vencedor.

Comentários são liberados somente ao cliente proprietário e ao técnico já atribuído. Um técnico que apenas visualiza a fila não pode listar nem publicar comentários. O telefone segue a mesma fronteira de atribuição.

### Estados e SLA

Transições operacionais pelo endpoint genérico:

| Origem | Destinos permitidos |
|---|---|
| `RECEBIDO` | `EM_TRIAGEM`, `EM_ATENDIMENTO` |
| `EM_TRIAGEM` | `EM_ATENDIMENTO`, `AGUARDANDO_CLIENTE` |
| `EM_ATENDIMENTO` | `AGUARDANDO_CLIENTE`, `AGUARDANDO_PECA` |
| `AGUARDANDO_CLIENTE` | `EM_ATENDIMENTO` |
| `AGUARDANDO_PECA` | `EM_ATENDIMENTO` |

`AGUARDANDO_PAGAMENTO`, `RESOLVIDO` e `FECHADO` não são destinos do endpoint genérico. Chamados com SLA pausado não podem mudar de estado nem ser finalizados; a pausa precisa ser retomada primeiro.

### Cobrança

`POST /api/tickets/{id}/finalize` exige o técnico atribuído e status `EM_ATENDIMENTO`. `valorFinal` é obrigatório, maior que zero e limitado a dez dígitos inteiros e duas casas decimais. O servidor define:

- `valor_final = valor informado`;
- `pagamento_realizado = false`;
- `status = AGUARDANDO_PAGAMENTO`;
- `resolvido_em = instante atual`, usado como fim técnico do SLA.

`POST /api/tickets/{id}/payment/confirm` exige o mesmo técnico, a cobrança existente e ainda não confirmada. O servidor define `pagamento_realizado = true` e `status = RESOLVIDO`.

Somente o cliente proprietário fecha um chamado resolvido, e apenas quando o pagamento está confirmado. Ao reabrir, valor, confirmação, resolução e fechamento anteriores são limpos para impedir que uma cobrança antiga contamine o novo ciclo.

## Ativos

Clientes criam e editam somente seus próprios ativos. O proprietário não pode ser trocado. Técnicos possuem leitura apenas quando o ativo está vinculado a um chamado livre legível ou a um chamado atribuído a eles; não podem enumerar o catálogo completo de clientes nem consultar ativos ligados exclusivamente a outro técnico. Técnicos não criam nem alteram o catálogo. Não há exclusão.

## Hardware e software

Consultas especializadas primeiro verificam leitura do chamado e depois o tipo correspondente. Mutação operacional de hardware e inclusão de logs de software exigem o técnico atribuído. O cliente proprietário pode manter os detalhes de contexto de software, mas não criar logs técnicos.

## Incidentes, notificações e tempo real

Incidentes são operados por técnicos. Notificações pertencem ao destinatário e outro usuário recebe `404` ao tentar marcá-las como lidas. SSE usa o JWT no cabeçalho, nunca na URL. Eventos enviados para a fila técnica livre usam a projeção sanitizada do chamado.

## Banco e Supabase

- acesso remoto somente pelo usuário JDBC restrito do backend;
- migrations controladas em `supabase/migrations/`;
- `docs/schema.sql` deve permanecer sincronizado;
- `ddl-auto=validate` no PostgreSQL;
- RLS continua habilitado como defesa adicional, embora o navegador não use a Data API;
- telefone usa `VARCHAR(20)` e check de formato internacional;
- valor usa `NUMERIC(12,2)` com check positivo;
- `pagamento_realizado` é `NOT NULL DEFAULT FALSE`;
- roles aceitas pelo banco: `CLIENTE`, `TECNICO`.

As migrations de endurecimento removem privilégios de tabelas, sequências e funções dos papéis Supabase `anon` e `authenticated`, inclusive nos privilégios padrão. O usuário JDBC `speeddesk_app` recebe apenas as permissões necessárias ao backend. Constraints adicionais impedem estados financeiros incoerentes, e sequências de identidade tornam determinística a ordenação de comentários e históricos técnicos criados no mesmo instante.

## Respostas e operação

- `401`: autenticação ausente, inválida ou expirada;
- `403`: identidade autenticada sem permissão ou cliente inadimplente;
- `400`: DTO inválido, UUID malformado ou regra de entrada;
- `404`: recurso inexistente ou oculto por propriedade privada;
- `409`: transição inválida, concorrência otimista ou duplicidade.

Problem Details não deve revelar stack trace, SQL, credenciais ou hashes. CORS aceita somente origens configuradas. OpenAPI e Swagger ficam desabilitados por padrão no perfil de produção. O Service Worker ignora qualquer caminho `/api/`, evitando cache de respostas privadas em uma futura hospedagem de mesma origem. O servidor estático local usa `frontend/serve.json` para preservar URLs com query string e aplicar cabeçalhos básicos de proteção. Produção deve usar HTTPS e JDBC com SSL obrigatório.

## Contas locais

O perfil `localdev` cria somente:

| Perfil | E-mail | Senha |
|---|---|---|
| `CLIENTE` | `cliente@speeddesk.local` | `SpeedDesk@123` |
| `TECNICO` | `tecnico@speeddesk.local` | `SpeedDesk@123` |
| `TECNICO` | `tecnico2@speeddesk.local` | `SpeedDesk@123` |

Essas credenciais são exclusivas do ambiente local.
