# Roadmap consolidado do Speed Desk

Este arquivo é a fonte de verdade do escopo escolhido. Ele separa o que já existe, o que ainda será construído e o que foi conscientemente descartado.

## Estado atual

### Implementado

- autenticação JWT, sessão web, BCrypt e autorização por `CLIENTE`, `TECNICO` e `GERENTE`;
- perfil `localdev` com H2 e dados de demonstração idempotentes;
- gestão administrativa de usuários com listagem, busca, filtros, criação, edição e ativação/desativação protegidas;
- configurações pessoais com consulta e edição de perfil e troca autenticada de senha;
- recuperação de senha por token temporário emitido pelo gerente para entrega manual;
- vínculo opcional de clientes a organizações integrado ao frontend;
- `A1–A6` gestão completa de ativos com datas e garantia, fabricante/modelo, tipo/status, edição, histórico de chamados e alertas;
- abertura, lista completa, busca, filtros, detalhe protegido e atribuição de chamados;
- `T4` transições controladas entre os sete estados canônicos;
- `T5` fechamento e reabertura com autorização do proprietário ou gerente;
- `T6` prazo e tempo restante de SLA expostos na lista, no painel e no detalhe;
- `T7` cadastro, consulta e uso das categorias no escopo aprovado;
- `C1` comentários públicos e `C2` notas internas com visibilidade por perfil;
- `SLA1` indicadores `ON_TRACK`, `AT_RISK`, `BREACHED`, `PAUSED` e `MET`;
- `SLA2` pausa controlada com motivo e retomada que preserva o tempo pausado;
- políticas de SLA por prioridade, snapshots por chamado e concorrência otimista;
- tipos `GERAL`, `HARDWARE` e `SOFTWARE`;
- cadastro e consulta de organizações;
- página administrativa de organizações e categorias;
- seleção e exibição de tipo e categoria no frontend;
- identidade visual completa com temas claro e escuro, fundo de velocidade e componentes responsivos;
- shell compartilhado de navegação por perfil, opções futuras desabilitadas e telas integradas de login, painel, usuários, ativos e configurações;
- área dedicada de chamados e consulta detalhada por UUID com autorização de proprietário;
- `HW1/HW4/HW7/HW9` fluxo de hardware com elegibilidade, garantia, etapas sequenciais, histórico técnico por ativo e checklist pós-reparo;
- `SW1–SW5/SW7` fluxo de software com versão, ambiente, plataforma/sistema operacional, reprodução, resultados e logs estruturados;
- `INC1` incidentes operacionais com severidade, status, serviço afetado e vínculos opcionais a chamados;
- `N1` notificações privadas por usuário, leitura individual/coletiva e eventos de chamados e incidentes;
- `AN3` exportações CSV de chamados, ativos e incidentes exclusivas do gerente;
- `RT1` atualizações autenticadas em tempo real por SSE para notificações, chamados, comentários e incidentes;
- `SEC2` idempotência persistida em operações críticas, com replay seguro por usuário;
- `SEC3` limite configurável de requisições públicas e autenticadas;
- `API1` especificação OpenAPI e Swagger UI com autenticação Bearer;
- testes automatizados do backend e integração frontend/backend validada localmente.

### Parcialmente implementado

| Código | Item | O que ainda falta |
| --- | --- | --- |
| `CFG2` | Configurações do gerente | Usuários, organizações, categorias, políticas de SLA e gestão de ativos estão integrados; controles dos módulos ainda futuros serão concluídos com esses módulos. |
| `ORG1` | Organizações | Cadastro e listagem básicos implementados. |

## Macroblocos na ordem recomendada

### 1. Estrutura visual e navegação do produto — concluído

- shell compartilhado e navegação por perfil implementados;
- áreas futuras exibidas como `Em breve`, sem links quebrados;
- temas claro e escuro, responsividade, mensagens e estados de carregamento padronizados;
- identidade visual do Google AI Studio adaptada ao frontend real do produto.

### 2. Núcleo completo de chamados — concluído

- `T1` lista completa — concluído;
- `T2` filtros e busca — concluído;
- `T3` detalhes — concluído;
- `T4` todas as transições permitidas — concluído;
- `T5` fechar e reabrir — concluído;
- `T6` exibição do SLA — concluído;
- `T7` categorias — concluído no escopo aprovado;
- `C1` comentários públicos — concluído;
- `C2` notas internas — concluído;
- `SLA1` risco de SLA — concluído;
- `SLA2` pausa controlada — concluído.

O bloco inclui política configurável por prioridade, snapshot por chamado, controle de concorrência e telas operacionais. Não inclui linha do tempo (`C3`) nem trilha de auditoria (`SEC1`).

### 3. Gestão de usuários e configurações

- `CFG1` configurações pessoais — concluído;
- `CFG2` configurações do gerente;
- `U1` tela de usuários — concluído;
- `U2` criação pelo gerente — concluído;
- `U3` edição — concluído;
- `U4` ativar/desativar — concluído;
- `U5` troca autenticada de senha — concluído;
- `U6` recuperação manual de senha — concluído, sem e-mail conforme o escopo;
- `ORG2` vínculo de usuários a organizações no frontend — concluído.

### 4. Gestão completa de ativos — concluído

- `A1` garantia e datas — concluído;
- `A2` fabricante e modelo — concluído;
- `A3` tipo e status — concluído;
- `A4` edição — concluído sem permitir a troca de proprietário;
- `A5` histórico de chamados por ativo — concluído;
- `A6` alertas de garantia — concluído para vencimentos em até 30 dias.

O catálogo usa tipos e status canônicos, serial único sem diferença entre maiúsculas e minúsculas, escopo por proprietário e projeção de garantia derivada. Técnicos possuem leitura; o cliente edita somente os próprios ativos e o gerente pode administrar o catálogo.

### 5. Fluxos especializados de hardware e software — concluído

Hardware:

- `HW1` garantia e elegibilidade — concluído;
- `HW4` etapas de manutenção — concluído, com avanço sequencial;
- `HW7` histórico técnico completo — concluído por chamado e consolidado por ativo;
- `HW9` checklist pós-reparo — concluído e obrigatório antes da etapa final.

Software:

- `SW1` versão — concluído;
- `SW2` ambiente afetado — concluído;
- `SW3` plataforma e sistema operacional — concluído;
- `SW4` passos para reprodução — concluído;
- `SW5` resultado esperado e atual — concluído;
- `SW7` logs técnicos estruturados — concluído nos níveis `DEBUG`, `INFO`, `WARN` e `ERROR`.

Os dados especializados aparecem apenas no tipo de chamado correspondente. O cliente proprietário pode manter os detalhes de software, enquanto ações operacionais de hardware e inclusão de logs exigem o técnico atribuído ou um gerente. O bloco não adiciona diagnóstico `HW2`, RMA, peças, logística, QR, correlação `SW6`, base de erros ou incidentes de software.

### 6. Operação, comunicação e relatórios — concluído

- `INC1` gestão geral de incidentes — concluído;
- `N1` notificações dentro do sistema — concluído;
- `AN3` exportação de relatórios — concluído;
- `RT1` atualizações em tempo real — concluído com SSE autenticado por Bearer Token.

Incidentes são operacionais: técnicos consultam e gerentes criam ou atualizam. As notificações pertencem exclusivamente ao destinatário, não usam e-mail e apontam para o recurso relacionado. As exportações são geradas em UTF-8 pela API Spring, sem acesso do frontend ao banco.

### 7. Segurança e documentação da API — concluído

- `SEC2` idempotência em operações críticas — concluído;
- `SEC3` limite de requisições — concluído;
- `API1` OpenAPI e Swagger — concluído.

### 8. Experiência avançada

- `PWA1` aplicação web instalável;
- `AI1` triagem de chamados com IA;
- `AI2` assistente de respostas com IA.

### 9. Banco remoto e ambiente de entrega

- migrations controladas alinharam o PostgreSQL/Supabase ao schema atual;
- índices, constraints, RLS e acesso exclusivo pela API Spring foram validados;
- manter em aberto as decisões de ambientes e hospedagem que ainda não foram aprovadas.

## Arquitetura futura do frontend

Nem toda funcionalidade deve virar um item de menu. A navegação planejada será:

### Todos os perfis

- **Painel**;
- **Chamados**: lista, filtros, busca e detalhes;
- **Notificações**;
- **Meu perfil**: dados pessoais e troca de senha — implementado;
- **Assistente IA** quando o módulo estiver disponível.

### Cliente

- **Meus chamados**;
- **Meus equipamentos**;
- abertura de chamado geral, hardware ou software;
- comentários públicos e acompanhamento de SLA — implementados.

### Técnico

- **Fila de atendimento**;
- **Hardware**;
- **Software**;
- **Incidentes**;
- notas internas — implementadas;
- etapas técnicas de hardware e registros de software — implementados.

### Gerente

- **Usuários**;
- **Ativos**;
- **Organizações**;
- **Categorias**;
- **Incidentes**;
- **Relatórios e exportações**;
- **Configurações administrativas**.

### Recursos internos, sem item de menu próprio

- transições de status, fechamento e reabertura — implementados;
- SLA, pausas e indicadores de risco — implementados;
- idempotência e rate limiting;
- tempo real;
- instalação PWA;
- OpenAPI/Swagger, acessível como documentação técnica;
- campos especializados de hardware/software dentro do chamado e do ativo — implementados.

## Itens conscientemente fora do escopo

- paginação de chamados (`T8`);
- refresh token/revogação e MFA (`AUTH-C`, `AUTH-D`);
- linha do tempo e anexos (`C3`, `ATT1`);
- painéis operacional e gerencial (`AN1`, `AN2`);
- e-mail (`N2`) e trilha de auditoria (`SEC1`);
- Actuator (`OBS1`);
- hardware: `HW2`, `HW3`, `HW5`, `HW6`, `HW8`;
- software: `SW6`, `SW8`, `SW9`;
- multi-tenant e SLA/ativos por organização (`ORG3`, `ORG4`, `ORG5`);
- Testcontainers, PostgreSQL local e acesso direto ao Supabase pelo frontend (`DB-B`, `DB-C`, `DB-D`);
- opções de deploy já rejeitadas (`DEP1` a `DEP5`);
- base de conhecimento (`KB1`, `KB2`);
- omnichannel, telefonia, QR móvel, comunidade, realidade aumentada e manutenção preditiva.

As decisões de ambiente que não foram explicitamente aprovadas ou rejeitadas continuam abertas.

A conclusão dos macroblocos 4 e 5 não reabre nenhum item rejeitado: os registros especializados implementados se limitam ao atendimento aprovado e não introduzem timeline geral, auditoria, anexos, multi-tenant, diagnóstico guiado, RMA, peças, logística, QR, correlação, base de erros ou incidentes de software.
