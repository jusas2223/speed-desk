# Speed Desk — escopo e roadmap do produto

Atualizado em 23 de agosto de 2026 após o aceite e a revisão de segurança do marketplace.

Este documento é a fonte de verdade do escopo funcional. O Speed Desk não é mais um helpdesk corporativo interno: ele conecta clientes a técnicos independentes para atendimentos físicos ou remotos.

## Princípios aprovados

- somente os perfis `CLIENTE` e `TECNICO` existem no domínio;
- o cliente é proprietário de seus ativos, chamados e dados de contato;
- chamados novos formam uma fila aberta visível a todos os técnicos;
- o primeiro técnico que assumir se torna o único responsável operacional;
- telefone, WhatsApp e chat são liberados ao técnico somente depois do aceite;
- o pagamento é combinado diretamente entre as partes;
- o técnico registra o valor cobrado e confirma o recebimento;
- clientes com pagamento pendente não podem abrir novos chamados;
- toda regra crítica é imposta pelo backend, mesmo quando o frontend antecipa o bloqueio;
- o navegador nunca acessa Supabase diretamente.

## Entregue

### Identidade e acesso

- login JWT, BCrypt, expiração, validação da conta atual e rate limiting;
- sessão web em `sessionStorage`;
- perfil próprio com nome, e-mail, telefone com DDI e troca de senha;
- navegação e autorização limitadas aos dois perfis aprovados;
- telas administrativas e permissões de administração central removidas.

### Marketplace de chamados

- criação de chamado exclusivamente pelo cliente proprietário;
- tipos `GERAL`, `HARDWARE` e `SOFTWARE`, prioridade, categoria e ativo opcional;
- fila do técnico composta por chamados `RECEBIDO`/`EM_TRIAGEM` sem responsável e chamados atribuídos a ele;
- aceite concorrente em nome próprio e início automático em `EM_ATENDIMENTO`;
- chamados de outro técnico não são legíveis nem operáveis;
- telefone, e-mail e organização do cliente não aparecem na fila livre e só são projetados para o cliente ou o técnico atribuído;
- link `https://wa.me/<numero>` e chat interno no detalhe após o aceite;
- comentários públicos entre cliente e técnico e notas técnicas internas do responsável;
- notificações privadas e atualização por SSE.

### Cobrança e inadimplência

- `valorFinal` com duas casas decimais e valor positivo;
- `pagamentoRealizado` não nulo, com padrão `false`;
- status `AGUARDANDO_PAGAMENTO`;
- finalização pelo técnico atribuído: `EM_ATENDIMENTO → AGUARDANDO_PAGAMENTO`;
- confirmação do recebimento pelo mesmo técnico: `AGUARDANDO_PAGAMENTO → RESOLVIDO`;
- valor cobrado visível ao cliente;
- endpoint dedicado de consulta da pendência;
- bloqueio transacional em `POST /api/tickets` quando existir cobrança pendente;
- banner e botão de novo chamado desabilitado no dashboard do cliente;
- fechamento pelo cliente somente após pagamento confirmado;
- reabertura limpa valor e confirmação anteriores, iniciando novo ciclo de atendimento.

### Ativos e fluxos técnicos preservados

- cadastro e edição dos próprios ativos pelo cliente;
- leitura contextual pelo técnico;
- garantia derivada, histórico técnico e vínculo de chamados;
- fluxo de hardware com elegibilidade, cobertura, etapas, manutenção e checklist;
- fluxo de software com ambiente, reprodução, resultados e logs estruturados;
- SLA por prioridade, pausa/retomada pelo técnico atribuído e projeção estável;
- incidentes e notificações operados por técnicos;
- PWA, temas claro/escuro, triagem e assistente de IA via backend Java.

## Estados canônicos do chamado

```text
RECEBIDO
  ├─ técnico assume → EM_ATENDIMENTO
  └─ preparação técnica → EM_TRIAGEM → EM_ATENDIMENTO

EM_ATENDIMENTO
  ├─ AGUARDANDO_CLIENTE → EM_ATENDIMENTO
  ├─ AGUARDANDO_PECA → EM_ATENDIMENTO
  └─ técnico informa valor → AGUARDANDO_PAGAMENTO

AGUARDANDO_PAGAMENTO
  └─ técnico confirma recebimento → RESOLVIDO

RESOLVIDO
  ├─ cliente fecha → FECHADO
  └─ cliente reabre → EM_ATENDIMENTO ou RECEBIDO

FECHADO
  └─ cliente reabre → EM_ATENDIMENTO ou RECEBIDO
```

O endpoint genérico de status não pode saltar para `AGUARDANDO_PAGAMENTO`, `RESOLVIDO` ou `FECHADO`.

## Fora do escopo atual

Os itens abaixo não devem ser introduzidos sem pedido explícito:

- perfil administrativo ou administração central de técnicos/clientes;
- atribuição de chamado a outro técnico;
- gateway de pagamento, PIX gerado pela plataforma, split, escrow, estorno ou conciliação bancária;
- comissão, assinatura, plano ou monetização automática do marketplace;
- geolocalização, raio de atendimento, agenda e despacho automático;
- avaliações, reputação, disputa, garantia comercial ou mediação;
- acesso do frontend ao Supabase;
- framework frontend;
- envio automático de senha ou recuperação por e-mail;
- relatórios administrativos e exportações CSV na interface;
- exclusão de ativos, comentários, logs ou histórico técnico;
- RMA, estoque de peças, logística, QR Code e base de erros conhecidos.

## Aceite do pivot concluído

- fluxo completo validado com uma conta cliente e duas contas técnicas;
- corrida de aceite confirmou um único vencedor e resposta de conflito ao concorrente;
- dados de contato ausentes na fila livre, liberados ao responsável e ocultos do segundo técnico;
- WhatsApp e chat liberados somente depois da atribuição;
- cobrança decimal, banner de pendência, bloqueio de criação, confirmação e fechamento validados;
- isolamento contextual de ativos e ordenação determinística dos históricos cobertos por regressão automatizada;
- privilégios da Data API removidos e advisor de segurança do Supabase sem alertas.

As próximas alterações devem se limitar a divergências reproduzíveis ou a evoluções aprovadas explicitamente.

## Evoluções futuras que exigem decisão de produto

- auto cadastro e verificação de identidade dos dois perfis;
- disponibilidade e região de atuação do técnico;
- pagamento integrado e modelo de receita;
- reputação e avaliações;
- recuperação de conta sem equipe administrativa;
- hospedagem definitiva e observabilidade de produção.

Nenhuma dessas evoluções está implicitamente autorizada pelo pivot atual.
