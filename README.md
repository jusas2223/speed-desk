# Speed Desk

Speed Desk é uma aplicação web de service desk para registrar ativos, abrir chamados e acompanhar o atendimento conforme o perfil do usuário. O projeto combina um frontend estático em JavaScript com uma API Spring Boot protegida por JWT.

## Estado atual

O projeto está em uma versão funcional de evolução. Login, sessão web, autorização por perfil, ciclo administrativo de usuários, configurações pessoais, gestão completa de ativos, núcleo de chamados e fluxos especializados de hardware e software estão implementados. A persistência local offline e a integração frontend/backend são cobertas por testes automatizados no backend. O frontend possui identidade visual responsiva, temas claro e escuro, navegação específica por perfil, área dedicada de chamados, perfil pessoal, catálogo de ativos e diretório administrativo de usuários. Organizações, categorias, tipos de chamado, comentários públicos, notas internas e políticas de SLA também estão integrados ao backend e às telas correspondentes.

## Tecnologias

- Java 26 e Spring Boot 4.1;
- Spring Web MVC, Spring Data JPA, Spring Security e OAuth2 Resource Server;
- JWT com HMAC SHA-256 e senhas BCrypt;
- PostgreSQL no ambiente remoto oficial e H2 no perfil local `localdev`;
- Maven Wrapper, JUnit 5, Mockito e MockMvc;
- HTML5, CSS3 e JavaScript com ES Modules e Fetch API.

## Estrutura

```text
speed-desk/
├── backend/   API Spring Boot, persistência, segurança e testes
├── frontend/  páginas HTML, estilos e módulos JavaScript
└── docs/      schema PostgreSQL de referência e documentação de segurança
```

A pasta `.speeddesk-local/` é criada durante o uso do H2 em arquivo e permanece fora do versionamento.

## Arquitetura

```text
Frontend HTML/CSS/JavaScript
        │ HTTP/JSON + Authorization: Bearer
        ▼
API Spring Boot
        │ Spring Data JPA / JDBC
        ▼
H2 localdev ou PostgreSQL/Supabase
```

O navegador se comunica somente com a API. A API autentica o JWT, aplica as regras de autorização e acessa o banco de dados.

## Perfis de usuário

| Perfil | Comportamento atual |
| --- | --- |
| `CLIENTE` | Visualiza, filtra, abre e acompanha somente os próprios chamados; cria, consulta e edita somente os próprios ativos. Comenta publicamente, mantém os dados especializados dos próprios chamados de software e pode fechar ou reabrir o chamado nos estados permitidos. Pode estar vinculado administrativamente a uma organização, sem compartilhar dados com outros clientes. |
| `TECNICO` | Consulta chamados e ativos de clientes, pode abrir chamados, comentar e assumir em seu próprio nome; status, SLA, manutenção de hardware e logs de software só podem ser operados quando ele for o técnico atribuído. Ativos permanecem somente para leitura. |
| `GERENTE` | Gerencia usuários e políticas de SLA, consulta, cria e edita recursos para clientes, visualiza qualquer chamado existente e pode atribuir, operar, fechar, reabrir e comentar chamados conforme as regras de negócio, incluindo os fluxos especializados. |

## Funcionalidades implementadas

- login com JWT e sessão armazenada no `sessionStorage`;
- inclusão automática do Bearer Token nas chamadas protegidas;
- expiração, logout e tratamento de respostas `401` e `403`;
- senhas BCrypt, normalização de e-mail e migração controlada de senhas legadas;
- autorização de recursos conforme `CLIENTE`, `TECNICO` e `GERENTE`;
- tela exclusiva do gerente para listar, buscar, filtrar, criar, editar, ativar e desativar usuários com vínculo opcional de clientes a organizações;
- bloqueio imediato de tokens quando a conta é desativada ou sua role é alterada;
- perfil pessoal autenticado para consultar e atualizar nome e e-mail;
- troca de senha mediante confirmação da senha atual;
- recuperação manual de senha por token temporário emitido pelo gerente, sem envio por e-mail;
- catálogo de ativos com fabricante, modelo, tipos canônicos, status, serial único sem diferença de maiúsculas, data de compra, garantia e fornecedor;
- busca e filtros de ativos, consulta individual, edição sem troca de proprietário e histórico de chamados por equipamento;
- projeção de garantia como `NAO_INFORMADA`, `VIGENTE`, `EXPIRA_EM_BREVE`, `EXPIRADA` ou `NAO_ELEGIVEL`, com alerta para vencimentos em até 30 dias;
- cadastro e consulta de organizações administrativas no backend e no frontend do gerente;
- cadastro e consulta de categorias ativas no backend e no frontend do gerente;
- abertura de chamados dos tipos `GERAL`, `HARDWARE` e `SOFTWARE`, com categoria e ativo opcionais, prioridade e prazo calculado por SLA;
- lista completa de chamados com busca e filtros combináveis por status, prioridade, tipo, categoria e responsável;
- página de detalhes persistente por UUID, protegida conforme o proprietário e o perfil autenticado;
- transições controladas entre os sete status canônicos, com fechamento e reabertura separados;
- prazo de SLA projetado como `ON_TRACK`, `AT_RISK`, `BREACHED`, `PAUSED` ou `MET`, incluindo tempo restante;
- políticas de duração e alerta por prioridade, configuráveis pelo gerente e copiadas para cada novo chamado;
- pausa de SLA com motivo e retomada que desloca o vencimento pelo tempo efetivamente pausado;
- comentários públicos e notas internas da equipe, ocultas de usuários `CLIENTE`;
- atendimento de hardware com elegibilidade, cobertura de garantia e progressão ordenada por `RECEBIDO`, `EM_ANALISE`, `EM_REPARO`, `EM_TESTE` e `CONCLUIDO`;
- registros de manutenção por chamado e histórico técnico consolidado por ativo, sem transformar esses dados em uma linha do tempo geral;
- checklist pós-reparo obrigatório antes da conclusão do fluxo de hardware;
- detalhes de software com versão, ambiente, plataforma, sistema operacional, reprodução e resultados esperado/atual;
- logs técnicos estruturados de software nos níveis `DEBUG`, `INFO`, `WARN` e `ERROR`;
- controle de concorrência otimista nas mutações de chamados, políticas e pausas de SLA;
- painel operacional responsivo com busca, filtros, métricas calculadas com dados reais e distribuição por status e categoria;
- shell compartilhado de navegação, temas claro e escuro, tela de login e identidade visual baseada em velocidade;
- autoatribuição de chamado pelo técnico e atribuição pelo gerente;
- resolução por técnico responsável ou gerente;
- respostas de erro no formato `ProblemDetail` e validação de entrada;
- perfil `localdev` com H2 persistente e dados locais de demonstração.

Os status canônicos são `RECEBIDO`, `EM_TRIAGEM`, `EM_ATENDIMENTO`, `AGUARDANDO_CLIENTE`, `AGUARDANDO_PECA`, `RESOLVIDO` e `FECHADO`. O fluxo normal permite `RECEBIDO → EM_TRIAGEM/EM_ATENDIMENTO`, `EM_TRIAGEM → EM_ATENDIMENTO/AGUARDANDO_CLIENTE`, `EM_ATENDIMENTO → AGUARDANDO_CLIENTE/AGUARDANDO_PECA/RESOLVIDO` e o retorno dos dois estados de espera para `EM_ATENDIMENTO`. Fechamento e reabertura usam operações próprias e não burlam essa matriz. As prioridades são `BAIXA`, `NORMAL`, `ALTA` e `CRITICA`. Os tipos de chamado são `GERAL`, `HARDWARE` e `SOFTWARE`; requests antigos sem tipo continuam sendo tratados como `GERAL`.

## Endpoints atuais

Todos os endpoints abaixo usam o prefixo `http://localhost:8080/api` durante o desenvolvimento local.

| Método | Rota | Acesso e finalidade |
| --- | --- | --- |
| `POST` | `/users/login` | Público. Autentica e devolve o JWT e os dados públicos do usuário. |
| `GET` | `/users` | Somente `GERENTE`. Lista usuários em ordem alfabética sem expor senhas. |
| `POST` | `/users` | Somente `GERENTE`. Cria usuário com senha codificada e organização opcional apenas para `CLIENTE`. |
| `PUT` | `/users/{userId}` | Somente `GERENTE`. Edita nome, e-mail, role e vínculo compatível com organização. |
| `PATCH` | `/users/{userId}/status` | Somente `GERENTE`. Ativa ou desativa uma conta com salvaguardas administrativas. |
| `POST` | `/users/{userId}/password-reset` | Somente `GERENTE`. Emite uma credencial temporária de recuperação para entrega manual ao usuário. |
| `GET` | `/account/profile` | Autenticado. Consulta os dados públicos da própria conta. |
| `PUT` | `/account/profile` | Autenticado. Atualiza nome e e-mail da própria conta. |
| `POST` | `/account/password/change` | Autenticado. Troca a senha depois de validar a senha atual. |
| `POST` | `/account/password-reset/confirm` | Público. Consome uma única vez o token temporário e define uma nova senha. |
| `GET` | `/organizations` | Somente `GERENTE`. Lista organizações por nome. |
| `POST` | `/organizations` | Somente `GERENTE`. Cria uma organização administrativa. |
| `GET` | `/ticket-categories` | Qualquer usuário autenticado. Lista categorias ativas por nome. |
| `POST` | `/ticket-categories` | Somente `GERENTE`. Cria uma categoria para um tipo de chamado. |
| `GET` | `/assets/cliente/{clienteId}` | Autenticado. Cliente acessa somente os próprios ativos; técnico e gerente podem consultar clientes. |
| `GET` | `/assets` | Autenticado. Lista ativos no escopo do perfil e aceita filtros por cliente, tipo, status, garantia e busca textual. |
| `POST` | `/assets` | Cliente ou gerente. Cria ativo vinculado a um usuário `CLIENTE`, respeitando o escopo de acesso. |
| `GET` | `/assets/warranty-alerts` | Autenticado. Lista, no escopo autorizado, garantias que vencem em até 30 dias. |
| `GET` | `/assets/{assetId}` | Autenticado. Consulta um ativo no escopo do perfil. |
| `PUT` | `/assets/{assetId}` | Cliente proprietário ou gerente. Edita o ativo sem permitir a troca de proprietário. |
| `GET` | `/assets/{assetId}/tickets` | Autenticado. Lista o histórico de chamados vinculados ao ativo autorizado. |
| `GET` | `/assets/{assetId}/technical-history` | Autenticado. Consolida os registros técnicos de chamados de hardware vinculados ao ativo autorizado. |
| `GET` | `/tickets` | Autenticado. Aceita filtros opcionais por cliente, status, prioridade, tipo, categoria, técnico, ausência de técnico e busca textual; o cliente permanece limitado aos próprios chamados. |
| `GET` | `/tickets/{ticketId}` | Autenticado. Cliente consulta somente chamado próprio; técnico e gerente podem consultar chamados existentes. |
| `POST` | `/tickets` | Autenticado. Abre chamado para um usuário `CLIENTE`, com tipo, categoria compatível e ativo do mesmo cliente opcionais. |
| `PATCH` | `/tickets/{ticketId}/assumir/{tecnicoId}` | Técnico assume em nome próprio ou gerente atribui a um técnico. |
| `PATCH` | `/tickets/{ticketId}/resolver` | Técnico atribuído ou gerente resolve chamado em atendimento. |
| `PATCH` | `/tickets/{ticketId}/status` | Técnico atribuído ou gerente executa uma transição permitida. O SLA precisa estar ativo. |
| `POST` | `/tickets/{ticketId}/close` | Cliente proprietário ou gerente fecha um chamado `RESOLVIDO`. |
| `POST` | `/tickets/{ticketId}/reopen` | Cliente proprietário ou gerente reabre um chamado `RESOLVIDO` ou `FECHADO` e reinicia o prazo a partir do snapshot de SLA. |
| `POST` | `/tickets/{ticketId}/sla/pause` | Técnico atribuído ou gerente pausa o SLA de chamado não concluído, com motivo obrigatório. |
| `POST` | `/tickets/{ticketId}/sla/resume` | Técnico atribuído ou gerente retoma o SLA e acrescenta ao vencimento o período pausado. |
| `GET` | `/tickets/{ticketId}/comments` | Autenticado e autorizado no chamado. Clientes recebem somente comentários públicos; a equipe recebe também notas internas. |
| `POST` | `/tickets/{ticketId}/comments` | Autenticado e autorizado no chamado. Cliente cria apenas comentário público; técnico e gerente também podem criar nota interna. |
| `GET` | `/tickets/{ticketId}/hardware` | Autenticado e autorizado no chamado `HARDWARE`. Consulta elegibilidade, garantia e etapa de manutenção. |
| `PUT` | `/tickets/{ticketId}/hardware` | Técnico atribuído ou gerente. Atualiza os detalhes e avança uma etapa por vez. |
| `GET` | `/tickets/{ticketId}/hardware/history` | Autenticado e autorizado. Lista o histórico técnico específico do atendimento de hardware. |
| `POST` | `/tickets/{ticketId}/hardware/history` | Técnico atribuído ou gerente. Registra uma intervenção de manutenção. |
| `GET` | `/tickets/{ticketId}/hardware/checklist` | Autenticado e autorizado. Consulta o checklist pós-reparo. |
| `PUT` | `/tickets/{ticketId}/hardware/checklist` | Técnico atribuído ou gerente. Atualiza o checklist nas etapas `EM_TESTE` ou `CONCLUIDO`. |
| `GET` | `/tickets/{ticketId}/software` | Autenticado e autorizado no chamado `SOFTWARE`. Consulta os detalhes para reprodução. |
| `PUT` | `/tickets/{ticketId}/software` | Cliente proprietário, técnico atribuído ou gerente. Cria ou atualiza os detalhes de software. |
| `GET` | `/tickets/{ticketId}/software/logs` | Autenticado e autorizado. Lista logs técnicos estruturados do chamado. |
| `POST` | `/tickets/{ticketId}/software/logs` | Técnico atribuído ou gerente. Registra um log técnico estruturado. |
| `GET` | `/sla-policies` | Qualquer usuário autenticado. Lista duração e alerta de SLA por prioridade. |
| `PUT` | `/sla-policies/{priority}` | Somente `GERENTE`. Atualiza a política usada por chamados criados posteriormente. |

Transições inválidas, operações incompatíveis com o estado do SLA e disputas de atualização concorrente retornam `409 Conflict` em `ProblemDetail`. Uma política nova não recalcula chamados existentes: duração e alerta ficam registrados no chamado quando ele é criado.

## Executar o backend com `localdev`

O perfil `localdev` foi criado para desenvolvimento offline, incluindo o uso do projeto dentro da faculdade. Ele usa H2 persistente em arquivo, em modo de compatibilidade PostgreSQL, sem acessar o Supabase por JDBC.

### PowerShell — forma recomendada

Na raiz do repositório, execute:

```powershell
.\start-local.ps1
```

O inicializador localiza o JDK 26 instalado, ativa o perfil `localdev`, configura apenas valores locais e usa sempre o banco existente em `.speeddesk-local/`, independentemente do diretório atual do terminal. IntelliJ e outras IDEs são opcionais para este fluxo.

### IntelliJ IDEA — opcional

1. Use o JDK 26 e abra uma configuração Spring Boot para `com.speeddesk.api.ApiApplication`.
2. Defina `C:\Meus Projetos\speed-desk` como diretório de trabalho.
3. Informe `localdev` no campo **Active profiles**.
4. Configure estas variáveis de ambiente com valores apenas locais:

```text
SPEEDDESK_JWT_SECRET=localdev-only-secret-with-at-least-32-bytes
SPEEDDESK_CORS_ALLOWED_ORIGINS=http://127.0.0.1:5500,http://localhost:5500
SPEEDDESK_LOCAL_DB_PATH=C:/Meus Projetos/speed-desk/.speeddesk-local/speeddesk
```

5. Execute `ApiApplication`.

As variáveis de banco `SPEEDDESK_DB_*` não são necessárias no perfil `localdev`. O banco será salvo em `.speeddesk-local/` na raiz do projeto. O H2 Console permanece desabilitado.

### Contas locais de demonstração

O seeder verifica cada registro individualmente e cria apenas os dados locais ausentes:

| Perfil | E-mail | Senha |
| --- | --- | --- |
| `GERENTE` | `gerente@speeddesk.local` | `SpeedDesk@123` |
| `TECNICO` | `tecnico@speeddesk.local` | `SpeedDesk@123` |
| `CLIENTE` | `cliente@speeddesk.local` | `SpeedDesk@123` |

Quando ausentes, são criadas ativas a organização `Empresa Demonstração` e as categorias `Solicitação geral` (`GERAL`), `Falha de equipamento` (`HARDWARE`) e `Erro de software` (`SOFTWARE`). Registros existentes são preservados sem reativação. `cliente@speeddesk.local` só é vinculado à organização quando sua role atual é `CLIENTE` e ainda não possui vínculo. Dados, roles e senhas de usuários existentes não são sobrescritos.

## Servir o frontend

O frontend usa ES Modules e deve ser servido por HTTP. Com o próprio JDK 26, execute na raiz do projeto:

```powershell
$env:JAVA_HOME="C:\Users\Pessoal\.jdks\openjdk-26.0.1"
& "$env:JAVA_HOME\bin\jwebserver.exe" -p 5500 -d frontend
```

Depois, acesse `http://localhost:5500/`. Também é possível usar um servidor estático da IDE na porta 5500. A API local deve estar disponível em `http://localhost:8080`.

## Variáveis de ambiente

O perfil padrão usa PostgreSQL e exige configuração externa. Os exemplos abaixo são fictícios:

| Variável | Finalidade | Exemplo fictício |
| --- | --- | --- |
| `SPEEDDESK_DB_URL` | URL JDBC do PostgreSQL com SSL obrigatório | `jdbc:postgresql://db.example.invalid:5432/postgres?sslmode=require` |
| `SPEEDDESK_DB_USERNAME` | Usuário do banco | `speeddesk_app` |
| `SPEEDDESK_DB_PASSWORD` | Senha do banco | `replace-with-a-secret` |
| `SPEEDDESK_JWT_SECRET` | Chave HMAC com pelo menos 32 bytes | `replace-with-a-random-32-byte-minimum-secret` |
| `SPEEDDESK_JWT_EXPIRATION_SECONDS` | Validade do token; padrão de 3600 segundos | `3600` |
| `SPEEDDESK_CORS_ALLOWED_ORIGINS` | Origens permitidas, separadas por vírgula | `http://127.0.0.1:5500,http://localhost:5500` |
| `SPEEDDESK_PASSWORD_RESET_EXPIRATION_MINUTES` | Validade da recuperação manual; padrão de 30 e intervalo aceito de 5 a 1440 minutos | `30` |
| `SPEEDDESK_RATE_LIMIT_ENABLED` | Ativa o limite de requisições; padrão `true` | `true` |
| `SPEEDDESK_AUTHENTICATED_REQUESTS_PER_MINUTE` | Limite por usuário autenticado; padrão 180 por minuto | `180` |
| `SPEEDDESK_PUBLIC_REQUESTS_PER_MINUTE` | Limite por IP nas rotas públicas; padrão 20 por minuto | `20` |

Nenhuma credencial real deve ser adicionada aos arquivos do projeto.

## Testes e validação

Para executar os testes do backend no PowerShell:

```powershell
cd "C:\Meus Projetos\speed-desk\backend"
$env:JAVA_HOME="C:\Users\Pessoal\.jdks\openjdk-26.0.1"
.\mvnw.cmd test
```

Para validar a sintaxe de todos os módulos JavaScript, execute na raiz:

```powershell
Get-ChildItem .\frontend\js -Filter *.js | ForEach-Object {
    node --check $_.FullName
}
```

Os testes automatizados usam H2 em memória e não tentam conectar ao Supabase.

## Bancos e ambientes

O H2 atende ao desenvolvimento offline, especialmente no ambiente de rede da faculdade. O PostgreSQL hospedado no Supabase continua sendo o banco remoto oficial do projeto e é usado pelo perfil padrão por meio das variáveis `SPEEDDESK_DB_*`.

O projeto remoto `ProjetoSpeedDesk` foi sincronizado em 22 de agosto de 2026 por migrations controladas. Ele possui as 18 tabelas atuais, RLS habilitado e uma policy exclusiva para o role JDBC `speeddesk_app`; os roles da Data API não recebem acesso. O arquivo `docs/schema.sql` continua sendo a representação PostgreSQL de referência, e `docs/supabase-access.sql` documenta os grants e policies do backend sem conter senha.

## Documentação da API e proteções operacionais

Com o backend em execução, a especificação OpenAPI fica em `http://localhost:8080/v3/api-docs` e a interface Swagger UI em `http://localhost:8080/swagger-ui.html`. O botão **Authorize** aceita o JWT emitido pelo login.

O frontend envia uma nova `Idempotency-Key` em operações autenticadas `POST`, `PUT` e `PATCH`. Em rotas críticas, o backend guarda somente os hashes da chave e da requisição por 24 horas; uma repetição idêntica devolve a resposta original sem executar a regra de negócio novamente. O rate limit devolve `429`, `Retry-After` e os cabeçalhos `X-RateLimit-*` quando o limite configurado é excedido.

As decisões definitivas sobre separação de ambientes, publicação do frontend e implantação do backend continuam em aberto. Novas alterações estruturais devem ser aplicadas como migrations antes de iniciar o perfil padrão, que usa `spring.jpa.hibernate.ddl-auto=validate` e nunca modifica o schema automaticamente.

## Roadmap

- implementar PWA e os dois recursos de IA;
- definir a estratégia final de ambientes e manter o schema remoto sincronizado por migrations controladas.

O escopo completo, as exclusões e a ordem dos macroblocos estão em [`docs/product-roadmap.md`](docs/product-roadmap.md). O fluxo técnico consolidado do Codex está em [`docs/development-workflow.md`](docs/development-workflow.md). Detalhes de segurança e operação local estão em [`docs/backend-security.md`](docs/backend-security.md).
