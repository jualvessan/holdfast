# ADR-0000: Stack base do projeto — Java 25 (LTS), Oracle Free em contêiner e Docker

## Status

Aceito

## Contexto

O Holdfast é o projeto principal do meu portfólio técnico, construído a partir de um feedback
recebido em processo seletivo (iFood — programa Elas São Tech), que apontou como pontos de
desenvolvimento: design de APIs REST, padrões de comunicação entre serviços, Clean Architecture,
DDD, modelagem de dados, sistemas distribuídos, escalabilidade e resiliência, além de experiência
prática com Redis, Kubernetes, CI/CD e cloud-native. Como ponto forte, o feedback destacou
conhecimento avançado em Oracle (otimização, tuning e particionamento).

O projeto simula uma plataforma de reserva de ingressos com estoque limitado, cujo desafio central
é garantir corretude sob alta concorrência (múltiplas pessoas disputando as mesmas unidades de
estoque ao mesmo tempo).

Antes de escrever qualquer código, era necessário decidir: a versão da linguagem, o banco de dados
transacional e a forma de rodar as dependências localmente e no CI, de modo a equilibrar
produtividade imediata, aderência ao que o mercado de vagas sênior de backend Java pratica, e
aproveitamento do conhecimento prévio em Oracle citado como ponto forte no feedback.

## Decisão

1. **Usar Java 25 (LTS)** como versão da linguagem e do runtime, com Spring Boot 3.5.x.
2. **Usar Oracle Database, na variante Oracle Free**, executado via a imagem de contêiner
   comunitária `gvenzl/oracle-free:23-slim`, tanto para desenvolvimento local quanto para os
   testes de integração (via Testcontainers).
3. **Usar Docker (Docker Desktop, com backend WSL2 no Windows)** como ferramenta de
   containerização, orquestrado localmente por Docker Compose.

## Consequências

### Positivas

- **Java 25 (LTS)** garante suporte de longo prazo (a próxima LTS, 29, só chega em 2027) e melhor
  compatibilidade com ferramentas de análise de bytecode usadas no projeto (Mockito/ByteBuddy,
  JaCoCo, ArchUnit, PIT), reduzindo o risco de bloqueios inesperados durante fases posteriores do
  roadmap (testes de arquitetura, mutation testing).
- **Oracle Free** permite reaproveitar diretamente o conhecimento prévio em tuning, otimização e
  particionamento citado como ponto forte no feedback, transformando-o em diferencial competitivo
  do portfólio (ex.: comparação de planos de execução antes/depois de particionar a tabela de
  reservas, na Fase 3).
- A imagem `gvenzl/oracle-free:23-slim` inicializa mais rápido que a imagem oficial da Oracle e
  possui módulo próprio no Testcontainers, simplificando a configuração dos testes de integração.
- **Docker** tem suporte nativo e sem configuração adicional em Testcontainers, no provider padrão
  do `kind` (Fase 6 — Kubernetes) e nos runners do GitHub Actions (Fase 7 — CI/CD), o que reduz a
  divergência entre ambiente local e ambiente de CI.
- Usar a mesma stack (Java/Spring/Oracle) do dia a dia profissional facilita explicar as decisões
  em entrevistas técnicas com profundidade real, em vez de conhecimento superficial de tecnologia
  nova.

### Negativas

- Java 25 não é a versão mais recente disponível (a 27 já existe), então o projeto abre mão de
  features de linguagem mais novas em troca de estabilidade de ferramental.
- Oracle Free tem limitações de recursos (CPU, memória, tamanho de dados) em relação a uma licença
  Oracle completa; alguns recursos avançados de particionamento ou tuning podem não estar
  disponíveis ou se comportar de forma distinta da edição Enterprise usada em ambiente corporativo
  real. Isso será verificado pontualmente quando cada recurso for implementado.
- Rodar Oracle em contêiner exige bastante memória (RAM alocada ao Docker/WSL2), o que pode gerar
  atrito em máquinas com poucos recursos.
- Docker Desktop tem termos de licenciamento comercial distintos de uso pessoal; para este projeto
  (uso pessoal, código aberto) não há restrição, mas a decisão precisaria ser revisitada caso o
  ambiente de desenvolvimento fosse corporativo.
- Senhas do banco em texto plano no `docker-compose.yml` são aceitáveis apenas em ambiente de
  desenvolvimento local; a Fase 6 (cloud-native) deve revisitar esse ponto com Secrets do
  Kubernetes.

## Alternativas consideradas

- **Java 27 (não-LTS):** rejeitado por ter janela de suporte curta (6 meses) e risco maior de
  incompatibilidade com ferramentas de teste e análise estática ainda em fase de adaptação à
  versão mais recente.
- **PostgreSQL:** é a escolha mais comum em portfólios de sistemas distribuídos e teria suporte de
  ferramentas igualmente maduro. Rejeitado porque não aproveitaria o diferencial de conhecimento
  prévio em Oracle explicitamente citado no feedback como ponto forte a ser destacado.
- **Oracle Database instalado diretamente na máquina (sem contêiner):** rejeitado por dificultar a
  reprodutibilidade do ambiente (outra pessoa, ou eu mesma em outra máquina, não conseguiria subir
  o projeto com um único comando) e por não se integrar com Testcontainers nem com o pipeline de
  CI.
- **Podman em vez de Docker:** rejeitado neste momento por exigir configuração adicional para
  funcionar com Testcontainers e com o `kind`, o que adicionaria atrito desnecessário logo no
  início do projeto. Pode ser revisitado futuramente como tópico de estudo comparativo.