# Fluxo de desenvolvimento do Speed Desk

Este documento registra a forma fixa de trabalho do projeto para evitar repetição de contexto e commits instáveis. A partir da fase final, o Codex executa o ciclo técnico completo diretamente no repositório, sem depender do IntelliJ ou do Antigravity.

## Responsabilidades

| Responsável | Papel |
| --- | --- |
| Codex | Define a arquitetura e a ordem dos macroblocos; implementa backend, frontend, segurança, persistência, testes e documentação; revisa e cria commits locais estáveis. |
| Usuário | Decide mudanças materiais de produto e autoriza publicação, push ou alterações em serviços externos quando necessário. |

O trabalho técnico é feito pela linha de comando e por edição direta dos arquivos. IntelliJ e Antigravity podem ser usados pelo usuário se desejar, mas não são dependências do processo.

## Ciclo obrigatório de um macrobloco

1. O Codex audita o estado real e seleciona um macrobloco do roadmap com limites claros.
2. Implementa primeiro o modelo, as regras, os endpoints e os testes necessários.
3. Implementa ou atualiza o frontend que consome os contratos aprovados.
4. Revisa autorização, validações, estados de erro, acessibilidade e responsividade.
5. Executa a suíte automatizada e testa o fluxo ponta a ponta com o perfil `localdev`.
6. Atualiza README, roadmap, schema de referência e documentação técnica aplicável.
7. Revisa o diff, segredos e artefatos locais antes de criar um commit estável.
8. Não realiza push ou alteração em serviço externo sem autorização específica do usuário.
9. O próximo macrobloco começa somente com o working tree limpo.

## Bancos e ambientes

- Na faculdade, o perfil `localdev` usa H2 e não depende de conexão JDBC com o Supabase.
- O PostgreSQL do Supabase continua sendo o banco remoto oficial.
- `docs/schema.sql` é uma referência; não é uma migration executada automaticamente.
- Alterações remotas devem usar SQL revisado e uma etapa controlada, sem expor credenciais no repositório.
- O frontend continua acessando somente a API Spring. Acesso direto ao Supabase permanece fora do escopo aprovado.
- Decisões definitivas de deploy e ambientes continuam abertas.

## Portões de qualidade

Antes de cada commit:

- executar `mvnw test` quando houver alteração de backend;
- executar `node --check` em todos os módulos JavaScript alterados;
- executar `git diff --check`;
- revisar `git status --short` e segredos acidentais;
- testar autorização por perfil e o fluxo principal afetado;
- em recursos consultados por UUID, cobrir `401`, `400`, `403`, `404` e tentativa de acesso a outro proprietário;
- testar filtros isolados, combinados, limpeza e resultado vazio;
- executar o fluxo ponta a ponta nos três perfis quando o comportamento variar por role;
- páginas novas devem reutilizar `navigation.js`, `theme.js` e o sistema visual compartilhado;
- páginas de detalhe devem funcionar por URL direta e após recarregamento;
- não versionar H2, `target/`, credenciais ou arquivos internos das ferramentas.

## Convenções de Git

- A branch principal atual é `main`.
- Cada commit deve representar um macrobloco estável ou uma correção isolada.
- Não usar `git add .` sem revisar antes o `git status`; ele é aceitável somente quando todos os arquivos exibidos já foram auditados.
- O push acontece depois da revisão do commit e da confirmação de que o working tree está limpo.
