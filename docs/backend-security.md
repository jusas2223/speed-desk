# Segurança do backend e da sessão web

O backend recebe toda configuração sensível pelo ambiente do processo. Não há senha de banco nem segredo JWT padrão na configuração principal.

Exemplo exclusivamente fictício:

```text
SPEEDDESK_DB_URL=jdbc:postgresql://db.example.invalid:5432/postgres
SPEEDDESK_DB_USERNAME=speeddesk_app
SPEEDDESK_DB_PASSWORD=replace-with-a-local-secret
SPEEDDESK_JWT_SECRET=replace-with-a-random-secret-containing-at-least-32-bytes
SPEEDDESK_JWT_EXPIRATION_SECONDS=3600
SPEEDDESK_CORS_ALLOWED_ORIGINS=http://127.0.0.1:5500,http://localhost:5500
```

`SPEEDDESK_CORS_ALLOWED_ORIGINS` aceita uma lista separada por vírgulas. Origens curinga são rejeitadas. O segredo JWT deve ter no mínimo 32 bytes em UTF-8 e deve ser aleatório e diferente em cada ambiente.

## Login, JWT e sessão no frontend

O login público é feito por `POST /api/users/login`. Após validar e-mail e senha, o backend devolve os dados públicos do usuário e um token JWT do tipo Bearer. O token contém o identificador, o e-mail e a role do usuário, possui expiração configurável e não contém a senha.

A integração JWT do frontend já está implementada em `frontend/js/api.js`:

- a sessão é armazenada no `sessionStorage` sob a chave `speeddesk_session`;
- o instante local de expiração é calculado a partir de `expiresIn`;
- cada chamada protegida recebe o cabeçalho `Authorization: Bearer <accessToken>`;
- sessões ausentes, inválidas ou expiradas são removidas antes da navegação para a tela de login;
- uma resposta `401 Unauthorized` em rota protegida limpa a sessão e redireciona para o login;
- uma resposta `403 Forbidden` preserva a sessão e entrega a mensagem `ProblemDetail` para a interface informar que a operação não é permitida;
- o logout remove a sessão e retorna à tela de login.

O backend também valida a assinatura, o emissor, a expiração e as claims obrigatórias do JWT. Com exceção do login e das requisições CORS `OPTIONS`, as rotas `/api/**` exigem autenticação.

## Permissões atuais

| Perfil | Permissões implementadas |
| --- | --- |
| `CLIENTE` | Lista, cria e acessa somente os próprios chamados e ativos. Pode consultar categorias ativas. Não pode assumir ou resolver chamados nem administrar usuários, organizações ou categorias. |
| `TECNICO` | Lista chamados e ativos de clientes, consulta categorias ativas, pode criar recursos para um cliente, assumir um chamado `RECEBIDO` somente em seu próprio nome e resolver apenas o chamado `EM_ATENDIMENTO` atribuído a ele. Não pode administrar usuários, organizações ou categorias. |
| `GERENTE` | Lista e cria usuários, organizações e categorias, consulta e cria recursos para clientes, atribui chamados recebidos a usuários `TECNICO` e pode resolver chamados em atendimento. |

O vínculo de um ativo ou chamado sempre exige um usuário com role `CLIENTE`. Um ativo informado na abertura do chamado precisa pertencer ao mesmo cliente. Apenas usuários `CLIENTE` podem receber uma organização, e esse agrupamento não altera as regras de proprietário nem concede acesso aos dados de outro cliente. Categorias precisam estar ativas e ter o mesmo tipo do chamado. As respostas usam DTOs e não expõem hashes de senha nem entidades JPA internas.

## RLS e Data API do Supabase

As tabelas remotas existentes estão com RLS habilitado e sem policies, mantendo bloqueado o acesso pela Data API. O schema de referência habilita RLS também em `organizations` e `ticket_categories`, mas essas definições novas não foram aplicadas ao Supabase remoto e permanecem pendentes de revisão. A API Spring continua sendo a única porta de entrada da aplicação e acessa o PostgreSQL pela conexão JDBC configurada no backend. O acesso direto ao Supabase pelo frontend permanece como decisão futura do bloco de ambiente; antes de aprová-lo, será necessário definir policies específicas. Nenhuma chave `service_role` deve ser exposta no navegador.

## Desenvolvimento offline com o perfil `localdev`

O perfil `localdev` usa um banco H2 persistente em arquivo exclusivamente para desenvolvimento offline. Ao executar o backend a partir da pasta `backend`, os arquivos ficam em `backend/.speeddesk-local/` e são ignorados pelo Git. O banco opera em modo de compatibilidade PostgreSQL, o schema é atualizado pelo Hibernate e o H2 Console permanece desabilitado.

No PowerShell:

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="localdev"
$env:SPEEDDESK_JWT_SECRET="localdev-only-secret-with-at-least-32-bytes"
$env:SPEEDDESK_CORS_ALLOWED_ORIGINS="http://127.0.0.1:5500,http://localhost:5500"
.\mvnw.cmd spring-boot:run
```

As variáveis `SPEEDDESK_DB_URL`, `SPEEDDESK_DB_USERNAME` e `SPEEDDESK_DB_PASSWORD` não são necessárias nesse perfil. O PostgreSQL hospedado no Supabase continua sendo o banco oficial dos ambientes padrão e remoto; o H2 não substitui nem altera esse banco.

O seeder idempotente cria apenas as contas locais ausentes:

| Perfil | E-mail | Senha |
| --- | --- | --- |
| `GERENTE` | `gerente@speeddesk.local` | `SpeedDesk@123` |
| `TECNICO` | `tecnico@speeddesk.local` | `SpeedDesk@123` |
| `CLIENTE` | `cliente@speeddesk.local` | `SpeedDesk@123` |

Quando ausentes, a organização `Empresa Demonstração` e uma categoria para cada tipo de chamado são criadas ativas. Registros existentes são preservados sem reativação. A conta do cliente local só recebe o vínculo administrativo quando sua role atual é `CLIENTE` e ainda não possui organização. As senhas são persistidas com o `PasswordEncoder` da aplicação. Reiniciar o backend não duplica nem sobrescreve usuários existentes.

## Senhas persistidas

Novos usuários são sempre gravados com BCrypt. No login, hashes BCrypt reconhecidos são validados pelo `PasswordEncoder`. Um valor legado claramente identificado como texto puro só é aceito por comparação exata e, após o primeiro login bem-sucedido, é substituído imediatamente por BCrypt. Formatos de hash desconhecidos são rejeitados e nunca são tratados como texto puro.

Essa compatibilidade é transitória. Contas legadas que nunca voltarem a fazer login continuarão exigindo uma migração controlada ou redefinição de senha. Depois que todas as contas forem migradas, o caminho de comparação legada deve ser removido.

## Pendências de implantação

- Configurar as variáveis de ambiente no runtime oficial do backend.
- Rotacionar no painel do provedor qualquer credencial externa que já tenha sido versionada.
- Migrar ou redefinir senhas legadas remanescentes.
- Definir a estratégia definitiva de ambientes e implantação.
