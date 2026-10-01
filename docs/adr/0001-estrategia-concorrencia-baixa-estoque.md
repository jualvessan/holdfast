# ADR-0001: Estratégia de concorrência para baixa de estoque via UPDATE condicional

## Status

Aceito

## Contexto

O problema central do Holdfast é garantir que, quando N pessoas disputam simultaneamente M
unidades de um setor (com N >> M), no máximo M reservas sejam confirmadas e nenhuma unidade seja
vendida duas vezes (overselling). Este é o requisito que a Fase 1 do roadmap chama de
"teste-assinatura": 200 threads disputando 100 unidades devem resultar em exatamente 100 sucessos
e 100 conflitos, de forma determinística.

O schema (ADR-0000, migração V2) já define a tabela `sector` com uma coluna `available`, que
representa as unidades ainda não reservadas, e uma coluna `version`, reservada mas não usada como
estratégia principal.

Existem, no ecossistema Oracle/JPA, ao menos três formas usuais de controlar concorrência nesse
tipo de baixa de estoque:

1. Locking otimista via `@Version` (JPA): lê a linha, tenta salvar, e o próprio Hibernate falha
   com `OptimisticLockException` se a versão mudou entre a leitura e a escrita.
2. Locking pessimista explícito via `SELECT ... FOR UPDATE`: lê a linha travando-a para escrita,
   processa, e só então libera o lock ao commitar.
3. `UPDATE` condicional atômico: uma única instrução SQL que já embute a condição de
   disponibilidade (`WHERE available >= :quantidade`) e decrementa o estoque na mesma operação,
   sem um `SELECT` prévio separado.

## Decisão

Usar **`UPDATE` condicional atômico** como mecanismo principal de controle de concorrência na
baixa de estoque, com a instrução:

```sql
UPDATE sector
   SET available = available - :quantidade
 WHERE id = :setorId
   AND available >= :quantidade
```

O número de linhas afetadas (`rowsUpdated`) é a fonte da verdade: se for `1`, a reserva prossegue;
se for `0`, não há estoque suficiente e a API responde `409 Conflict`. Não há `SELECT` de leitura
do `available` antes desse `UPDATE` para fins de decisão — qualquer leitura anterior é apenas
informativa (ex.: exibir disponibilidade na tela), nunca a base da decisão de vender ou não.

A coluna `version` permanece no schema, mas não é usada como mecanismo de controle nesta fase.

## Consequências

### Positivas

- **Correção sob concorrência garantida pelo próprio banco**, sem depender de uma etapa de leitura
  seguida de escrita, que é justamente onde condições de corrida (race conditions) costumam
  aparecer em implementações ingênuas.
- **Sem lock explícito de linha** (`FOR UPDATE`) mantido durante processamento de aplicação: o
  `UPDATE` é uma operação atômica e curta, o que reduz a janela de contenção comparado a manter uma
  linha travada enquanto lógica de aplicação roda.
- **Sem round-trip adicional**: uma única instrução resolve leitura da condição e escrita, ao
  contrário do padrão otimista (que precisa de um `SELECT` anterior) ou pessimista (que também
  parte de uma leitura).
- Simples de testar de forma determinística: o teste de concorrência da Fase 1 dispara N threads
  chamando o mesmo `UPDATE` e verifica que o total de linhas afetadas com sucesso nunca ultrapassa
  o estoque inicial.
- Facilmente explicável em entrevista técnica, por ser uma única instrução auditável, sem
  "mágica" de framework.

### Negativas

- **Não retorna o objeto atualizado por padrão** (diferente de um `SELECT ... FOR UPDATE` seguido
  de `UPDATE`, onde a aplicação já tem a entidade carregada em memória). Se o caso de uso precisar
  do valor de `available` pós-decremento, é necessário um `SELECT` adicional, ou usar a cláusula
  `RETURNING` do Oracle.
- **Menos "natural" em relação ao Spring Data JPA / Hibernate**, que tende a favorecer o padrão
  carregar-entidade-e-salvar. A implementação usará `@Modifying` com JPQL/SQL nativo em vez de
  `save()` convencional para essa operação específica, o que é uma exceção deliberada ao uso
  típico do Spring Data no restante do projeto.
- **Não resolve, sozinho, a necessidade de expirar holds não confirmados** (a unidade decrementada
  precisa voltar ao estoque se a reserva expirar). Isso é tratado por um mecanismo complementar
  (job de expiração com `FOR UPDATE SKIP LOCKED`, previsto para a Fase 3), não coberto por esta
  ADR.
- Se, no futuro, o caso de uso evoluir para decrementar estoque de **múltiplos setores em uma
  única reserva** (carrinho multi-item), múltiplos `UPDATE`s na mesma transação precisarão de uma
  ordem consistente de acesso às linhas para evitar deadlock — isso será revisitado se e quando o
  carrinho multi-item for implementado.

## Alternativas consideradas

- **Locking otimista via `@Version` (JPA):** rejeitado como mecanismo principal porque exige uma
  leitura prévia da entidade, e sob alta contenção (muitas threads disputando a mesma linha) gera
  uma taxa alta de `OptimisticLockException`, exigindo lógica de retry na aplicação para o cliente
  não ver um erro genérico. A coluna `version` foi mantida no schema para permitir revisitar esta
  alternativa caso um cenário futuro (ex.: edição concorrente de dados menos disputados) se
  beneficie dela.
- **Locking pessimista via `SELECT ... FOR UPDATE`:** rejeitado como mecanismo principal porque
  mantém uma linha travada durante todo o processamento da transação na aplicação, aumentando a
  janela de contenção e o risco de timeouts em cascata sob carga alta, além de ser mais difícil de
  testar de forma determinística (o comportamento depende do tempo de processamento entre o lock e
  o commit). Continua sendo a ferramenta certa para outro problema do projeto: o job de expiração
  de holds (Fase 3) usará `FOR UPDATE SKIP LOCKED`, que tem propósito diferente (permitir múltiplas
  instâncias do job processarem lotes distintos sem se bloquearem, não decidir uma venda).
- **Lock distribuído via Redis (ex.: Redlock):** rejeitado por adicionar uma dependência externa a
  um problema que o próprio banco transacional já resolve nativamente e de forma mais simples.
  Seria overengineering: seu principal caso de uso é coordenar múltiplos processos fora de um
  banco de dados comum, o que não é o cenário aqui (todas as instâncias da aplicação acessam o
  mesmo Oracle).
