# Fluxo de desenvolvimento do Speed Desk

Este documento registra a forma fixa de trabalho do projeto para evitar troca de responsabilidades, repetição de contexto e commits instáveis.

## Responsabilidades

| Responsável | Papel |
| --- | --- |
| Mentor desta conversa | Define arquitetura, ordem dos macroblocos, critérios de aceite, prompts e revisão final. |
| Codex executor | Implementa backend, regras de negócio, segurança, persistência, testes automatizados e documentação técnica correspondente. |
| Antigravity | Implementa e revisa o frontend HTML/CSS/JavaScript, responsividade, acessibilidade e experiência visual. |
| Usuário | Valida decisões de produto, realiza o teste visual final quando necessário e autoriza publicação ou mudanças externas. |

Exceção já encerrada: o frontend de organizações e categorias foi inicialmente criado pelo Codex e revisado pelo Antigravity. Essa inversão não deve virar o padrão.

## Ciclo obrigatório de um macrobloco

1. O mentor seleciona um macrobloco do roadmap e define dependências e limites.
2. O Codex implementa o backend e os testes, sem iniciar o próximo bloco.
3. O mentor revisa o código e solicita somente correções comprovadas.
4. A suíte automatizada passa e o backend recebe um commit estável.
5. O Antigravity implementa o frontend que consome os endpoints aprovados.
6. O mentor revisa segurança, integração e responsividade.
7. O fluxo é testado ponta a ponta em H2 local ou temporário.
8. O frontend recebe um commit estável.
9. O usuário faz o push, ou autoriza explicitamente que ele seja realizado.
10. O próximo macrobloco começa somente com o working tree limpo.

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
- não versionar H2, `target/`, credenciais ou arquivos internos das ferramentas.

## Convenções de Git

- A branch principal atual é `main`.
- Cada commit deve representar um macrobloco estável ou uma correção isolada.
- Não usar `git add .` sem revisar antes o `git status`; ele é aceitável somente quando todos os arquivos exibidos já foram auditados.
- O push acontece depois da revisão do commit e da confirmação de que o working tree está limpo.
