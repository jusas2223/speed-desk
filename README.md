# Speed Desk

Speed Desk é uma aplicação web de service desk para registrar ativos, abrir chamados e acompanhar o atendimento conforme o perfil do usuário. O projeto combina um frontend estático em JavaScript com uma API Spring Boot protegida por JWT.

## Estado atual

O projeto está em uma versão funcional de evolução. Login, sessão web, autorização por perfil, gestão básica de usuários, ativos e chamados, persistência local offline e integração frontend/backend estão implementados e cobertos por testes automatizados no backend. O frontend possui identidade visual responsiva, temas claro e escuro, navegação específica por perfil e uma área dedicada de chamados com consulta individual protegida. Organizações, categorias e tipos de chamado também estão integrados ao backend e ao frontend administrativo.

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
| `CLIENTE` | Visualiza, filtra e consulta individualmente somente os próprios ativos e chamados. Pode estar vinculado administrativamente a uma organização, sem compartilhar dados com outros clientes. |
| `TECNICO` | Visualiza, filtra e consulta individualmente chamados e ativos de clientes, pode criar recursos para clientes, assumir chamados em seu próprio nome e resolver os que estiverem atribuídos a ele. |
| `GERENTE` | Gerencia usuários, consulta e cria recursos para clientes, visualiza qualquer chamado existente, atribui chamados a técnicos e pode resolver chamados em atendimento. |

## Funcionalidades implementadas

- login com JWT e sessão armazenada no `sessionStorage`;
- inclusão automática do Bearer Token nas chamadas protegidas;
- expiração, logout e tratamento de respostas `401` e `403`;
- senhas BCrypt, normalização de e-mail e migração controlada de senhas legadas;
- autorização de recursos conforme `CLIENTE`, `TECNICO` e `GERENTE`;
- cadastro e consulta de ativos por cliente;
- cadastro e consulta de organizações administrativas no backend e no frontend do gerente;
- cadastro e consulta de categorias ativas no backend e no frontend do gerente;
- abertura de chamados dos tipos `GERAL`, `HARDWARE` e `SOFTWARE`, com categoria e ativo opcionais, prioridade e prazo calculado por SLA;
- lista completa de chamados com busca e filtros combináveis por status, prioridade, tipo, categoria e responsável;
- página de detalhes persistente por UUID, protegida conforme o proprietário e o perfil autenticado;
- painel operacional responsivo com busca, filtros, métricas calculadas com dados reais e distribuição por status e categoria;
- shell compartilhado de navegação, temas claro e escuro, tela de login e identidade visual baseada em velocidade;
- autoatribuição de chamado pelo técnico e atribuição pelo gerente;
- resolução por técnico responsável ou gerente;
- respostas de erro no formato `ProblemDetail` e validação de entrada;
- perfil `localdev` com H2 persistente e dados locais de demonstração.

Os status canônicos são `RECEBIDO`, `EM_TRIAGEM`, `EM_ATENDIMENTO`, `AGUARDANDO_CLIENTE`, `AGUARDANDO_PECA`, `RESOLVIDO` e `FECHADO`. As prioridades são `BAIXA`, `NORMAL`, `ALTA` e `CRITICA`. Os tipos de chamado são `GERAL`, `HARDWARE` e `SOFTWARE`; requests antigos sem tipo continuam sendo tratados como `GERAL`.

## Endpoints atuais

Todos os endpoints abaixo usam o prefixo `http://localhost:8080/api` durante o desenvolvimento local.

| Método | Rota | Acesso e finalidade |
| --- | --- | --- |
| `POST` | `/users/login` | Público. Autentica e devolve o JWT e os dados públicos do usuário. |
| `GET` | `/users` | Somente `GERENTE`. Lista usuários sem expor senhas. |
| `POST` | `/users` | Somente `GERENTE`. Cria usuário com senha codificada e organização opcional apenas para `CLIENTE`. |
| `GET` | `/organizations` | Somente `GERENTE`. Lista organizações por nome. |
| `POST` | `/organizations` | Somente `GERENTE`. Cria uma organização administrativa. |
| `GET` | `/ticket-categories` | Qualquer usuário autenticado. Lista categorias ativas por nome. |
| `POST` | `/ticket-categories` | Somente `GERENTE`. Cria uma categoria para um tipo de chamado. |
| `GET` | `/assets/cliente/{clienteId}` | Autenticado. Cliente acessa somente os próprios ativos; técnico e gerente podem consultar clientes. |
| `POST` | `/assets` | Autenticado. Cria ativo vinculado a um usuário `CLIENTE`, respeitando o escopo de acesso. |
| `GET` | `/tickets` | Autenticado. Aceita filtros opcionais por cliente, status, prioridade, tipo, categoria, técnico, ausência de técnico e busca textual; o cliente permanece limitado aos próprios chamados. |
| `GET` | `/tickets/{ticketId}` | Autenticado. Cliente consulta somente chamado próprio; técnico e gerente podem consultar chamados existentes. |
| `POST` | `/tickets` | Autenticado. Abre chamado para um usuário `CLIENTE`, com tipo, categoria compatível e ativo do mesmo cliente opcionais. |
| `PATCH` | `/tickets/{ticketId}/assumir/{tecnicoId}` | Técnico assume em nome próprio ou gerente atribui a um técnico. |
| `PATCH` | `/tickets/{ticketId}/resolver` | Técnico atribuído ou gerente resolve chamado em atendimento. |

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

O frontend usa ES Modules e deve ser servido por HTTP. Com Python disponível, execute na raiz do projeto:

```powershell
python -m http.server 5500 --directory frontend
```

Depois, acesse `http://localhost:5500/`. Também é possível usar um servidor estático da IDE na porta 5500. A API local deve estar disponível em `http://localhost:8080`.

## Variáveis de ambiente

O perfil padrão usa PostgreSQL e exige configuração externa. Os exemplos abaixo são fictícios:

| Variável | Finalidade | Exemplo fictício |
| --- | --- | --- |
| `SPEEDDESK_DB_URL` | URL JDBC do PostgreSQL | `jdbc:postgresql://db.example.invalid:5432/postgres` |
| `SPEEDDESK_DB_USERNAME` | Usuário do banco | `speeddesk_app` |
| `SPEEDDESK_DB_PASSWORD` | Senha do banco | `replace-with-a-secret` |
| `SPEEDDESK_JWT_SECRET` | Chave HMAC com pelo menos 32 bytes | `replace-with-a-random-32-byte-minimum-secret` |
| `SPEEDDESK_JWT_EXPIRATION_SECONDS` | Validade do token; padrão de 3600 segundos | `3600` |
| `SPEEDDESK_CORS_ALLOWED_ORIGINS` | Origens permitidas, separadas por vírgula | `http://127.0.0.1:5500,http://localhost:5500` |

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

As decisões definitivas sobre separação de ambientes, publicação do frontend e implantação do backend continuam em aberto. O arquivo `docs/schema.sql` é a representação PostgreSQL de referência do estado atual; ele não é executado automaticamente como migração.

## Roadmap

- consolidar o núcleo completo de chamados, usuários, ativos, SLA e comentários;
- adicionar os fluxos especializados de hardware e software;
- implementar notificações, incidentes, exportações, tempo real, PWA e IA;
- definir a estratégia final de ambientes e sincronizar o schema remoto de forma controlada.

O escopo completo, as exclusões e a ordem dos macroblocos estão em [`docs/product-roadmap.md`](docs/product-roadmap.md). O fluxo técnico consolidado do Codex está em [`docs/development-workflow.md`](docs/development-workflow.md). Detalhes de segurança e operação local estão em [`docs/backend-security.md`](docs/backend-security.md).
