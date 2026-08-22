# Segurança do backend e da sessão web

O backend recebe toda configuração sensível pelo ambiente do processo. Não há senha de banco nem segredo JWT padrão na configuração principal.

Exemplo exclusivamente fictício:

```text
SPEEDDESK_DB_URL=jdbc:postgresql://db.example.invalid:5432/postgres?sslmode=require
SPEEDDESK_DB_USERNAME=speeddesk_app
SPEEDDESK_DB_PASSWORD=replace-with-a-local-secret
SPEEDDESK_JWT_SECRET=replace-with-a-random-secret-containing-at-least-32-bytes
SPEEDDESK_JWT_EXPIRATION_SECONDS=3600
SPEEDDESK_CORS_ALLOWED_ORIGINS=http://127.0.0.1:5500,http://localhost:5500
SPEEDDESK_PASSWORD_RESET_EXPIRATION_MINUTES=30
```

`SPEEDDESK_CORS_ALLOWED_ORIGINS` aceita uma lista separada por vírgulas. Origens curinga são rejeitadas. O segredo JWT deve ter no mínimo 32 bytes em UTF-8 e deve ser aleatório e diferente em cada ambiente. A validade da recuperação manual usa 30 minutos por padrão; `SPEEDDESK_PASSWORD_RESET_EXPIRATION_MINUTES` aceita valores entre 5 e 1440 minutos.

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

O backend também valida a assinatura, o emissor, a expiração e as claims obrigatórias do JWT. Em cada requisição protegida, a conta correspondente precisa continuar existente, ativa e com a mesma role contida no token. Assim, desativar uma conta ou alterar sua role administrativa invalida imediatamente tokens já emitidos, sem implementar refresh token ou uma lista de revogação. Com exceção do login, da confirmação pública de recuperação e das requisições CORS `OPTIONS`, as rotas `/api/**` exigem autenticação.

## Permissões atuais

| Perfil | Permissões implementadas |
| --- | --- |
| `CLIENTE` | Consulta e atualiza o próprio perfil, troca a própria senha, lista, filtra, abre e acompanha somente os próprios chamados; cria, consulta e edita somente os próprios ativos. Pode comentar publicamente, manter os detalhes dos próprios chamados de software e fechar ou reabrir o chamado nos estados permitidos, além de consultar categorias e políticas de SLA. Não pode assumir, operar status/SLA ou hardware, criar logs/notas internas nem administrar cadastros. |
| `TECNICO` | Consulta e atualiza o próprio perfil, troca a própria senha, lista e consulta chamados e ativos de clientes, consulta categorias e políticas de SLA, pode abrir chamados, comentar e assumir em seu próprio nome. Ativos são somente leitura; status, SLA, manutenção de hardware, detalhes e logs de software só podem ser operados quando for o técnico atribuído. Não pode fechar/reabrir chamados nem administrar políticas ou cadastros. |
| `GERENTE` | Consulta e atualiza o próprio perfil, troca a própria senha, administra contas, recuperações manuais, organizações, categorias, políticas de SLA e ativos. Pode atribuir, operar, fechar, reabrir e comentar qualquer chamado conforme as regras de estado, além de operar todos os fluxos especializados. |

O vínculo de um ativo ou chamado sempre exige um usuário ativo com role `CLIENTE`. Um ativo informado na abertura do chamado precisa pertencer ao mesmo cliente. Apenas usuários `CLIENTE` podem receber uma organização, e esse agrupamento não altera as regras de proprietário nem concede acesso aos dados de outro cliente. Categorias precisam estar ativas e ter o mesmo tipo do chamado. As respostas usam DTOs e não expõem hashes de senha nem entidades JPA internas.

### Gestão administrativa de usuários

`GET /api/users`, `POST /api/users`, `PUT /api/users/{userId}` e `PATCH /api/users/{userId}/status` são exclusivos de `GERENTE`. A listagem e as mutações retornam apenas `UserResponseDTO`, inclusive o estado ativo, sem expor senhas. E-mails são normalizados e comparados sem diferença de maiúsculas, senhas respeitam o limite do BCrypt em UTF-8 e são codificadas antes da persistência. A organização é opcional para `CLIENTE` e rejeitada para `TECNICO` ou `GERENTE`.

Um gerente não pode desativar a própria conta nem alterar a própria role. O sistema preserva pelo menos um gerente ativo e bloqueia mudanças de role que deixariam ativos, chamados ou atribuições associados a um perfil incompatível. Contas inativas não autenticam, não podem ser escolhidas como cliente ou técnico de novas operações e têm tokens existentes rejeitados. A página `usuarios.html` oferece edição, filtros de status e confirmação visual de ativação/desativação, mas o backend permanece como autoridade de segurança.

### Perfil pessoal e troca autenticada de senha

`GET /api/account/profile` e `PUT /api/account/profile` exigem autenticação e sempre operam sobre o identificador da conta contido no JWT. O usuário pode alterar somente nome e e-mail; role, organização e estado ativo continuam sob controle administrativo. O novo e-mail é normalizado e precisa permanecer único sem diferença entre maiúsculas e minúsculas.

`POST /api/account/password/change` exige a senha atual correta, rejeita reutilização da mesma senha e valida o limite de 72 bytes em UTF-8 do BCrypt. A nova senha é persistida somente como BCrypt e todos os tokens de recuperação ainda não usados daquela conta são invalidados. Como refresh token e revogação de sessão estão fora do escopo aprovado, JWTs já emitidos continuam válidos até a própria expiração, salvo se a conta for desativada ou sua role mudar.

### Recuperação manual de senha

O envio por e-mail não faz parte do escopo. Um gerente inicia o fluxo por `POST /api/users/{userId}/password-reset`; a resposta devolve o identificador e o nome do usuário, o token temporário e sua expiração para entrega manual. O valor bruto aparece somente nessa resposta e nunca é persistido nem registrado pelo backend.

Cada token usa 32 bytes gerados por `SecureRandom` e codificados em Base64 URL-safe. A tabela `password_reset_tokens` armazena apenas o SHA-256 hexadecimal do valor, a conta, criação, expiração e instante de uso. Uma nova emissão é serializada por usuário e invalida tokens anteriores ainda não utilizados. `POST /api/account/password-reset/confirm` é público, mas aceita somente um token existente, não expirado e ainda não usado; a leitura aplica bloqueio pessimista para impedir consumo concorrente, e a mesma transação troca a senha e invalida todos os tokens pendentes da conta. A mensagem de falha é a mesma para token inexistente, expirado ou consumido.

Redefinir a senha de uma conta inativa não a reativa. A autenticação continua bloqueada até um gerente alterar explicitamente o estado da conta.

### Consulta de chamados por UUID e filtros

`GET /api/tickets/{ticketId}` aplica autorização sobre o objeto encontrado: sem token a resposta é `401`, UUID malformado produz `400`, UUID válido inexistente produz `404` e um cliente tentando consultar chamado de outro proprietário recebe `403`. Técnico e gerente podem consultar chamados existentes. A resposta usa `TicketResponseDTO` e não expõe hashes, entidades JPA ou campos internos.

Os filtros de listagem e a busca textual são aplicados somente depois que o escopo do usuário é determinado. Portanto, parâmetros como `clienteId`, `tecnicoId`, `semTecnico`, status, prioridade, tipo ou categoria nunca ampliam o conjunto autorizado de um cliente.

### Estados, fechamento e reabertura de chamados

O endpoint `PATCH /api/tickets/{ticketId}/status` pode ser usado somente pelo técnico atualmente atribuído ou por um gerente. Ele aplica a seguinte matriz explícita:

| Estado atual | Próximos estados permitidos |
| --- | --- |
| `RECEBIDO` | `EM_TRIAGEM`, `EM_ATENDIMENTO` |
| `EM_TRIAGEM` | `EM_ATENDIMENTO`, `AGUARDANDO_CLIENTE` |
| `EM_ATENDIMENTO` | `AGUARDANDO_CLIENTE`, `AGUARDANDO_PECA`, `RESOLVIDO` |
| `AGUARDANDO_CLIENTE` | `EM_ATENDIMENTO` |
| `AGUARDANDO_PECA` | `EM_ATENDIMENTO` |

A entrada em `EM_ATENDIMENTO` exige técnico atribuído. `RESOLVIDO` e `FECHADO` não aceitam transições pelo endpoint genérico, e qualquer mudança de status é bloqueada enquanto o SLA estiver pausado. `PATCH /api/tickets/{ticketId}/resolver` permanece como atalho sujeito à mesma autorização e à transição `EM_ATENDIMENTO → RESOLVIDO`.

`POST /api/tickets/{ticketId}/close` fecha somente um chamado `RESOLVIDO`; `POST /api/tickets/{ticketId}/reopen` aceita `RESOLVIDO` ou `FECHADO`. Essas duas operações são exclusivas do cliente proprietário ou de um gerente. Ao reabrir, o chamado volta a `EM_ATENDIMENTO` se ainda possuir técnico ativo; caso contrário, perde a atribuição e volta a `RECEBIDO`. A resolução e o fechamento anteriores são limpos e um novo prazo completo começa usando o snapshot de SLA já registrado no chamado.

### Políticas, projeção e pausa de SLA

`GET /api/sla-policies` exige autenticação. `PUT /api/sla-policies/{priority}` é exclusivo de `GERENTE` e valida duração entre 1 e 43200 minutos, alerta entre 0 e 10080 minutos e alerta estritamente menor que a duração. Na ausência de configuração persistida, os padrões são criados de forma idempotente: `CRITICA` 240/60, `ALTA` 1440/240, `NORMAL` 2880/480 e `BAIXA` 4320/720 minutos de duração/alerta.

Ao abrir um chamado, duração e alerta da prioridade são copiados para o próprio registro. Alterar uma política afeta chamados futuros, sem recalcular retroativamente prazos existentes. O `TicketResponseDTO` expõe o vencimento, o tempo restante em segundos e um estado derivado: `ON_TRACK`, `AT_RISK`, `BREACHED`, `PAUSED` ou `MET`. Em chamados resolvidos ou fechados a projeção usa o instante da resolução, preservando o resultado do SLA.

`POST /api/tickets/{ticketId}/sla/pause` e `/sla/resume` são permitidos apenas ao técnico atribuído ou a um gerente. A pausa exige motivo não vazio de até 500 caracteres, não é aceita em chamado concluído e não pode ser duplicada. A retomada fecha o registro de pausa ativo e desloca `data_vencimento` pelo período efetivamente pausado. Os registros em `ticket_sla_pauses` são evidência operacional necessária ao cálculo e não implementam a linha do tempo `C3` nem a trilha de auditoria `SEC1`, ambas fora do escopo.

Chamados, políticas e registros de pausa possuem versão de concorrência otimista. Duas operações que tentem persistir a mesma versão não se sobrescrevem silenciosamente: a segunda recebe `409 Conflict` em `ProblemDetail` e deve atualizar os dados antes de tentar novamente. Transições de estado e operações de SLA incompatíveis também retornam `409`.

### Comentários públicos e notas internas

`GET` e `POST /api/tickets/{ticketId}/comments` exigem autenticação e primeiro aplicam a autorização de leitura do chamado. O cliente acessa somente comentários públicos dos próprios chamados e não pode marcar uma publicação como interna. Técnicos e gerentes podem consultar e criar tanto comentários públicos quanto notas internas. O conteúdo é aparado, obrigatório e limitado a 4000 caracteres; a resposta contém apenas dados públicos do autor.

Não existem endpoints para editar ou apagar comentários. Notas internas nunca são devolvidas a um cliente, mesmo que ele seja o proprietário do chamado. A coleção ordenada de comentários é uma conversa do chamado e não representa a linha do tempo geral rejeitada no item `C3`.

### Ativos, garantia e histórico por equipamento

`GET /api/assets`, `/api/assets/{assetId}`, `/api/assets/{assetId}/tickets`, `/api/assets/{assetId}/technical-history` e `/api/assets/warranty-alerts` calculam primeiro o escopo do usuário. Um cliente sempre é limitado ao próprio identificador, mesmo que tente fornecer outro `clienteId`; técnico e gerente podem consultar ativos de clientes. O endpoint legado `GET /api/assets/cliente/{clienteId}` permanece disponível com a mesma proteção.

`POST /api/assets` é permitido ao cliente para si próprio e ao gerente para um cliente ativo. `PUT /api/assets/{assetId}` é permitido somente ao proprietário ou ao gerente, e rejeita qualquer tentativa de trocar o proprietário. Técnicos possuem acesso somente de leitura. Não há endpoint de exclusão.

O modelo aceita apenas os tipos `NOTEBOOK`, `DESKTOP`, `MONITOR`, `IMPRESSORA`, `SERVIDOR`, `EQUIPAMENTO_REDE`, `PERIFERICO` e `OUTRO`, e os status `ATIVO`, `EM_MANUTENCAO`, `INATIVO` e `DESCARTADO`. O serial é normalizado e permanece único sem diferença entre maiúsculas e minúsculas. A data final da garantia não pode anteceder a compra. O estado de garantia não é aceito do navegador: ele é derivado no backend como `NAO_INFORMADA`, `VIGENTE`, `EXPIRA_EM_BREVE` para até 30 dias, `EXPIRADA` ou `NAO_ELEGIVEL` para ativos inativos/descartados.

A resposta mantém os aliases legados `nome`/`numeroSerie` junto aos nomes canônicos `modelo`/`serial`. O conversor de persistência também preserva a leitura do H2 anterior: valores conhecidos como `Computador` ou `Laptop` são convertidos para o tipo canônico correspondente e um tipo histórico desconhecido é lido como `OUTRO`; toda nova escrita usa o nome canônico.

### Atendimento especializado de hardware

As rotas `/api/tickets/{ticketId}/hardware/**` primeiro validam a autorização de leitura e depois exigem que o chamado seja `HARDWARE`. Consultas são permitidas a quem já pode ler o chamado. Atualização de elegibilidade/garantia, inclusão de manutenção e checklist exigem o técnico atribuído ou um gerente.

As etapas avançam uma posição por vez em `RECEBIDO → EM_ANALISE → EM_REPARO → EM_TESTE → CONCLUIDO`; repetir a etapa atual é idempotente quanto à progressão e saltos ou retornos são rejeitados. O checklist só pode ser preenchido em `EM_TESTE` ou `CONCLUIDO`, contém cinco verificações obrigatórias para ser considerado completo e precisa estar concluído antes da etapa final. Depois da conclusão, ele não pode voltar a ficar incompleto enquanto o atendimento permanecer concluído.

Mudanças de etapa, intervenções manuais e conclusão do checklist produzem registros imutáveis em `hardware_maintenance_history`. `GET /api/assets/{assetId}/technical-history` consolida somente esses registros de chamados vinculados ao ativo autorizado. Esse histórico técnico é necessário ao item `HW7`; ele não cria a linha do tempo geral `C3`, auditoria `SEC1`, diagnóstico `HW2`, RMA, peças, logística ou QR, todos fora do escopo.

### Atendimento especializado de software

As rotas `/api/tickets/{ticketId}/software/**` primeiro aplicam a autorização do chamado e rejeitam qualquer tipo diferente de `SOFTWARE`. Os detalhes registram versão, ambiente `PRODUCAO`, `HOMOLOGACAO`, `DESENVOLVIMENTO`, `TESTE` ou `OUTRO`, plataforma, sistema operacional, passos de reprodução e resultados esperado/atual. Cliente proprietário, técnico atribuído e gerente podem manter esses detalhes.

Os logs técnicos estruturados usam `DEBUG`, `INFO`, `WARN` ou `ERROR`, origem, mensagem e instante da ocorrência. Qualquer usuário autorizado pode consultá-los, mas somente o técnico atribuído ou um gerente pode incluir um registro. Logs não possuem edição nem exclusão. Não foram adicionados IDs de correlação (`SW6`), base de erros conhecidos (`SW8`) ou gestão de incidentes de software (`SW9`).

## RLS e Data API do Supabase

O PostgreSQL remoto foi sincronizado por migrations controladas em 22 de agosto de 2026. As 14 tabelas da aplicação estão com RLS habilitado. Cada uma possui a policy `speeddesk_backend_access`, destinada exclusivamente ao role JDBC `speeddesk_app`; esse role recebe somente conexão, uso do schema e operações de dados necessárias ao backend. Ele não possui privilégios de superusuário, criação de banco, criação de role ou alteração de schema.

`anon` e `authenticated` não aparecem em nenhuma policy da aplicação. Portanto, a Data API continua bloqueada, e o frontend acessa dados somente pela API Spring. Nenhuma chave `service_role`, senha JDBC ou segredo JWT deve ser exposto no navegador. A senha do role `speeddesk_app` é criada fora das migrations e mantida apenas como variável de ambiente local ou do runtime.

O script reproduzível sem segredo está em `docs/supabase-access.sql`. Novas tabelas devem receber RLS, grants para `speeddesk_app` e a mesma policy na migration que as criar. Detalhes, checklists, históricos e logs são removidos por `ON DELETE CASCADE` quando o chamado é excluído; usuários responsáveis continuam protegidos por `ON DELETE RESTRICT`. Índices acompanham os acessos por ticket, ativo, data de manutenção e ocorrência do log, e o serial do ativo usa índice único sobre `LOWER(serial_tag)`.

## Desenvolvimento offline com o perfil `localdev`

O perfil `localdev` usa um banco H2 persistente em arquivo exclusivamente para desenvolvimento offline. O inicializador oficial mantém os arquivos em `.speeddesk-local/`, na raiz do repositório, e eles são ignorados pelo Git. O banco opera em modo de compatibilidade PostgreSQL, o schema é atualizado pelo Hibernate e o H2 Console permanece desabilitado.

No PowerShell:

```powershell
.\start-local.ps1
```

O script define `SPEEDDESK_LOCAL_DB_PATH` com um caminho absoluto calculado a partir do próprio repositório. Isso evita a criação acidental de bancos H2 diferentes conforme o diretório de execução. As variáveis `SPEEDDESK_DB_URL`, `SPEEDDESK_DB_USERNAME` e `SPEEDDESK_DB_PASSWORD` não são necessárias nesse perfil. O PostgreSQL hospedado no Supabase continua sendo o banco oficial dos ambientes padrão e remoto; o H2 não substitui nem altera esse banco.

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
- Aplicar futuras alterações estruturais por migrations antes de iniciar a versão correspondente do backend.
- Rotacionar no painel do provedor qualquer credencial externa que já tenha sido versionada.
- Migrar ou redefinir senhas legadas remanescentes.
- Definir a estratégia definitiva de ambientes e implantação.
