# Speed Desk

Speed Desk é uma aplicação web de service desk para registrar ativos, abrir chamados e acompanhar o atendimento conforme o perfil do usuário. O projeto combina um frontend estático em JavaScript com uma API Spring Boot protegida por JWT.

## Estado atual

O projeto está em uma versão funcional de estabilização. Login, sessão web, autorização por perfil, gestão básica de usuários, ativos e chamados, persistência local offline e integração frontend/backend estão implementados e cobertos por testes automatizados no backend.

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
| `CLIENTE` | Visualiza e cadastra os próprios ativos e chamados. Não acessa recursos de outro cliente. |
| `TECNICO` | Visualiza chamados e ativos de clientes, pode criar recursos para clientes, assumir chamados em seu próprio nome e resolver os que estiverem atribuídos a ele. |
| `GERENTE` | Gerencia usuários, consulta e cria recursos para clientes, atribui chamados a técnicos e pode resolver chamados em atendimento. |

## Funcionalidades implementadas

- login com JWT e sessão armazenada no `sessionStorage`;
- inclusão automática do Bearer Token nas chamadas protegidas;
- expiração, logout e tratamento de respostas `401` e `403`;
- senhas BCrypt, normalização de e-mail e migração controlada de senhas legadas;
- autorização de recursos conforme `CLIENTE`, `TECNICO` e `GERENTE`;
- cadastro e consulta de ativos por cliente;
- abertura de chamados com prioridade, ativo opcional e prazo calculado por SLA;
- painel Kanban com chamados recebidos, em atendimento e concluídos;
- autoatribuição de chamado pelo técnico e atribuição pelo gerente;
- resolução por técnico responsável ou gerente;
- respostas de erro no formato `ProblemDetail` e validação de entrada;
- perfil `localdev` com H2 persistente e dados locais de demonstração.

Os status canônicos são `RECEBIDO`, `EM_TRIAGEM`, `EM_ATENDIMENTO`, `AGUARDANDO_CLIENTE`, `AGUARDANDO_PECA`, `RESOLVIDO` e `FECHADO`. As prioridades são `BAIXA`, `NORMAL`, `ALTA` e `CRITICA`.

## Endpoints atuais

Todos os endpoints abaixo usam o prefixo `http://localhost:8080/api` durante o desenvolvimento local.

| Método | Rota | Acesso e finalidade |
| --- | --- | --- |
| `POST` | `/users/login` | Público. Autentica e devolve o JWT e os dados públicos do usuário. |
| `GET` | `/users` | Somente `GERENTE`. Lista usuários sem expor senhas. |
| `POST` | `/users` | Somente `GERENTE`. Cria usuário com senha codificada. |
| `GET` | `/assets/cliente/{clienteId}` | Autenticado. Cliente acessa somente os próprios ativos; técnico e gerente podem consultar clientes. |
| `POST` | `/assets` | Autenticado. Cria ativo vinculado a um usuário `CLIENTE`, respeitando o escopo de acesso. |
| `GET` | `/tickets` | Autenticado. Aceita `clienteId` opcional; cliente é sempre limitado aos próprios chamados. |
| `POST` | `/tickets` | Autenticado. Abre chamado para um usuário `CLIENTE`, com ativo opcional do mesmo cliente. |
| `PATCH` | `/tickets/{ticketId}/assumir/{tecnicoId}` | Técnico assume em nome próprio ou gerente atribui a um técnico. |
| `PATCH` | `/tickets/{ticketId}/resolver` | Técnico atribuído ou gerente resolve chamado em atendimento. |

## Executar o backend com `localdev`

O perfil `localdev` foi criado para desenvolvimento offline, incluindo o uso do projeto dentro da faculdade. Ele usa H2 persistente em arquivo, em modo de compatibilidade PostgreSQL, sem acessar o Supabase por JDBC.

### IntelliJ IDEA

1. Use o JDK 26 e abra uma configuração Spring Boot para `com.speeddesk.api.ApiApplication`.
2. Defina `C:\Meus Projetos\speed-desk\backend` como diretório de trabalho.
3. Informe `localdev` no campo **Active profiles**.
4. Configure estas variáveis de ambiente com valores apenas locais:

```text
SPEEDDESK_JWT_SECRET=localdev-only-secret-with-at-least-32-bytes
SPEEDDESK_CORS_ALLOWED_ORIGINS=http://127.0.0.1:5500,http://localhost:5500
```

5. Execute `ApiApplication`.

As variáveis de banco `SPEEDDESK_DB_*` não são necessárias no perfil `localdev`.

### PowerShell

```powershell
cd "C:\Meus Projetos\speed-desk\backend"
$env:JAVA_HOME="C:\Users\Pessoal\.jdks\openjdk-26.0.1"
$env:SPRING_PROFILES_ACTIVE="localdev"
$env:SPEEDDESK_JWT_SECRET="localdev-only-secret-with-at-least-32-bytes"
$env:SPEEDDESK_CORS_ALLOWED_ORIGINS="http://127.0.0.1:5500,http://localhost:5500"
.\mvnw.cmd spring-boot:run
```

O banco será salvo em `backend/.speeddesk-local/`. O H2 Console permanece desabilitado.

### Contas locais de demonstração

As contas abaixo são criadas apenas quando o perfil `localdev` encontra a tabela de usuários vazia:

| Perfil | E-mail | Senha |
| --- | --- | --- |
| `GERENTE` | `gerente@speeddesk.local` | `SpeedDesk@123` |
| `TECNICO` | `tecnico@speeddesk.local` | `SpeedDesk@123` |
| `CLIENTE` | `cliente@speeddesk.local` | `SpeedDesk@123` |

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

- definir a estratégia final de ambientes e implantação;
- ampliar a validação automatizada da experiência do frontend;
- evoluir o fluxo operacional de chamados a partir do uso real, sem comprometer a base estabilizada.

Detalhes de segurança e operação local estão em [`docs/backend-security.md`](docs/backend-security.md).
