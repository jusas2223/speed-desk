# Roadmap consolidado do Speed Desk

Este arquivo é a fonte de verdade do escopo escolhido. Ele separa o que já existe, o que ainda será construído e o que foi conscientemente descartado.

## Estado atual

### Implementado

- autenticação JWT, sessão web, BCrypt e autorização por `CLIENTE`, `TECNICO` e `GERENTE`;
- perfil `localdev` com H2 e dados de demonstração idempotentes;
- cadastro e consulta básicos de usuários pelo backend;
- cadastro e consulta básicos de ativos;
- abertura, listagem em Kanban, atribuição e resolução básicas de chamados;
- prioridades, prazo básico de SLA, tipos `GERAL`, `HARDWARE` e `SOFTWARE`;
- cadastro e consulta de organizações e categorias;
- página administrativa de organizações e categorias;
- seleção e exibição de tipo e categoria no frontend;
- identidade visual completa com temas claro e escuro, fundo de velocidade e componentes responsivos;
- shell compartilhado de navegação por perfil, opções futuras desabilitadas e telas integradas de login, painel, ativos e configurações;
- testes automatizados do backend e integração frontend/backend validada localmente.

### Parcialmente implementado

| Código | Item | O que ainda falta |
| --- | --- | --- |
| `CFG2` | Configurações do gerente | Ampliar para usuários, regras de SLA e demais parâmetros futuros. |
| `T1` | Lista completa de chamados | O painel já usa uma lista operacional; falta a área completa de chamados e a página de detalhes. |
| `T2` | Filtros e busca | Busca e filtros básicos existem no painel; faltam filtros completos por status, prioridade, categoria e responsável. |
| `T4` | Transições controladas | Existem atribuição e resolução; faltam as demais transições e regras. |
| `T6` | Prazo de SLA | O prazo é calculado, mas falta exposição completa, risco e pausa. |
| `T7` | Categorias | Cadastro e uso básico prontos; edição/ativação não fazem parte do escopo atual. |
| `U1` | Gestão de usuários | Backend lista usuários; falta a tela administrativa. |
| `U2` | Criação de usuários | Backend cria; falta a experiência completa no frontend. |
| `ORG1` | Organizações | Cadastro e listagem básicos implementados. |
| `ORG2` | Usuários por organização | Vínculo existe no backend; falta integrá-lo à gestão de usuários. |

## Macroblocos restantes, na ordem recomendada

### 1. Estrutura visual e navegação do produto — concluído

- shell compartilhado e navegação por perfil implementados;
- áreas futuras exibidas como `Em breve`, sem links quebrados;
- temas claro e escuro, responsividade, mensagens e estados de carregamento padronizados;
- identidade visual do Google AI Studio adaptada ao frontend real do produto.

### 2. Núcleo completo de chamados

- `T1` lista completa;
- `T2` filtros e busca;
- `T3` detalhes;
- `T4` todas as transições permitidas;
- `T5` fechar e reabrir;
- `T6` exibição do SLA;
- `T7` categorias já iniciadas;
- `C1` comentários públicos;
- `C2` notas internas;
- `SLA1` risco de SLA;
- `SLA2` pausa controlada.

### 3. Gestão de usuários e configurações

- `CFG1` configurações pessoais;
- `CFG2` configurações do gerente;
- `U1` tela de usuários;
- `U2` criação pelo gerente;
- `U3` edição;
- `U4` ativar/desativar;
- `U5` troca autenticada de senha;
- `U6` recuperação de senha;
- concluir `ORG2` no frontend.

### 4. Gestão completa de ativos

- `A1` garantia e datas;
- `A2` fabricante e modelo;
- `A3` tipo e status;
- `A4` edição;
- `A5` histórico de chamados por ativo;
- `A6` alertas de garantia.

### 5. Fluxos especializados de hardware e software

Hardware:

- `HW1` garantia e elegibilidade;
- `HW4` etapas de manutenção;
- `HW7` histórico técnico completo;
- `HW9` checklist pós-reparo.

Software:

- `SW1` versão;
- `SW2` ambiente afetado;
- `SW3` plataforma e sistema operacional;
- `SW4` passos para reprodução;
- `SW5` resultado esperado e atual;
- `SW7` logs técnicos estruturados.

### 6. Operação, comunicação e relatórios

- `INC1` gestão geral de incidentes;
- `N1` notificações dentro do sistema;
- `AN3` exportação de relatórios;
- `RT1` atualizações em tempo real.

### 7. Segurança e documentação da API

- `SEC2` idempotência em operações críticas;
- `SEC3` limite de requisições;
- `API1` OpenAPI e Swagger.

### 8. Experiência avançada

- `PWA1` aplicação web instalável;
- `AI1` triagem de chamados com IA;
- `AI2` assistente de respostas com IA.

### 9. Banco remoto e ambiente de entrega

- criar migration controlada para alinhar o PostgreSQL/Supabase ao schema atual;
- validar índices, constraints, RLS e acesso exclusivo pela API Spring;
- manter em aberto as decisões de ambientes e hospedagem que ainda não foram aprovadas.

## Arquitetura futura do frontend

Nem toda funcionalidade deve virar um item de menu. A navegação planejada será:

### Todos os perfis

- **Painel**;
- **Chamados**: lista, filtros, busca e detalhes;
- **Notificações**;
- **Meu perfil**: dados pessoais e troca de senha;
- **Assistente IA** quando o módulo estiver disponível.

### Cliente

- **Meus chamados**;
- **Meus equipamentos**;
- abertura de chamado geral, hardware ou software;
- comentários públicos e acompanhamento de SLA.

### Técnico

- **Fila de atendimento**;
- **Hardware**;
- **Software**;
- **Incidentes**;
- notas internas e etapas técnicas.

### Gerente

- **Usuários**;
- **Ativos**;
- **Organizações**;
- **Categorias**;
- **Incidentes**;
- **Relatórios e exportações**;
- **Configurações administrativas**.

### Recursos internos, sem item de menu próprio

- transições de status, fechamento e reabertura;
- SLA, pausas e indicadores de risco;
- idempotência e rate limiting;
- tempo real;
- instalação PWA;
- OpenAPI/Swagger, acessível como documentação técnica;
- campos especializados de hardware/software dentro do chamado e do ativo.

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
