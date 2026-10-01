# Troubleshooting: Testcontainers + Docker Desktop + Oracle no Windows

## Contexto

Durante a execução do teste de integração `SectorConcurrentReservationIT` (Testcontainers +
Oracle Free + Flyway), o ambiente apresentou duas falhas em sequência: primeiro uma falha de
comunicação com o Docker, depois uma falha de timeout na inicialização do Oracle. As duas
pareciam, à primeira vista, o mesmo tipo de problema — não eram.

Ambiente:

- Windows
- IntelliJ IDEA
- JDK 25
- Maven 3.10.0
- Docker Desktop 4.93.0 / Docker Engine 29.8.1
- Spring Boot 3.5.0
- Testcontainers inicialmente na versão 1.20.6
- Oracle Free: `gvenzl/oracle-free:23-slim`

## Problema 1 — Testcontainers não conseguia acessar o Docker

**Sintoma**

```
dial tcp [::1]:2375: connectex:
No connection could be made because the target machine actively refused it.
```

O Docker Java / Testcontainers tentava acessar `tcp://localhost:2375`, mas o Docker Desktop não
disponibilizava o daemon nesse endpoint.

**Diagnóstico**

O Docker Desktop estava funcionando normalmente, mas o Docker CLI estava usando o contexto
`default`, com uma variável `DOCKER_HOST` apontando para `tcp://localhost:2375`. No Windows, o
Docker Desktop expõe o daemon Linux via named pipe (`npipe:////./pipe/dockerDesktopLinuxEngine`),
não via TCP — o contexto ativo estava simplesmente apontando para o lugar errado.

**Solução**

```
docker context ls
docker context use desktop-linux
docker version
docker ps
```

Depois da troca de contexto, ambos os comandos passaram a funcionar.

**Regra de diagnóstico**

Quando o Testcontainers falhar para acessar o Docker no Windows, verificar nesta ordem:

1. O Docker Desktop está rodando?
2. `docker version` funciona?
3. `docker ps` funciona?
4. `docker context ls` — qual é o contexto ativo?
5. O contexto correto (`desktop-linux`) está em uso?
6. Existe algum `DOCKER_HOST` configurado incorretamente no ambiente?

Neste caso, o problema não era do Oracle nem do Testcontainers em si — era o endpoint usado para
chegar ao Docker.

## Problema 2 — Incompatibilidade entre Testcontainers antigo e Docker Engine 29

Resolvida a comunicação com o Docker, surgiu uma segunda falha: o Testcontainers (BOM na versão
`1.20.6`) não se comunicava corretamente com o Docker Engine `29.8.1` (API `1.56`).

**Diagnóstico**

A investigação mostrou que a linha de versão do Testcontainers usada no projeto não era
compatível com uma versão tão recente do Docker Engine.

**Solução**

Atualizar o BOM do Testcontainers:

```xml
<!-- Antes -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-bom</artifactId>
    <version>1.20.6</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<!-- Depois -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-bom</artifactId>
    <version>1.21.4</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

Não foi necessário fixar versões individuais de cada módulo — o Maven resolveu automaticamente
`testcontainers`, `testcontainers:junit-jupiter` e `testcontainers:oracle-free` para `1.21.4`, e
`docker-java-api` para `3.4.2`.

## Validação intermediária

Com as duas correções aplicadas, o log passou a mostrar:

```
Testcontainers version: 1.21.4
Found Docker environment with local Npipe socket (npipe:////./pipe/docker_engine)
Connected to docker:
  Server Version: 29.8.1
  API Version: 1.56
  Operating System: Docker Desktop
```

A comunicação Java → Testcontainers → Docker estava resolvida, e o container auxiliar Ryuk subiu
normalmente.

## Problema 3 — Oracle não ficava pronto dentro do timeout padrão

Com Docker e Testcontainers funcionando, uma terceira falha apareceu: o container
`gvenzl/oracle-free:23-slim` iniciava corretamente, mas o Testcontainers desistia de esperar pelo
indicador `DATABASE IS READY TO USE!` antes de o banco realmente terminar de subir.

**Diagnóstico**

O log confirmava que o Oracle não estava falhando — estava avançando normalmente pelas etapas de
inicialização (`starting up` → `initializing` → `uncompressing database data files` →
`starting Oracle Database` → `listener` → `Oracle instance started` → `Database mounted` → ...),
só que mais devagar do que o timeout padrão do Testcontainers permitia.

**Solução**

```java
import java.time.Duration;

@Container
private static final OracleContainer ORACLE = new OracleContainer(
        DockerImageName.parse("gvenzl/oracle-free:23-slim"))
        .withUsername("holdfast")
        .withPassword("holdfast_dev")
        .withStartupTimeout(Duration.ofMinutes(3));
```

Não foi necessário trocar imagem, versão do banco, Docker ou estratégia de espera — só ampliar o
tempo de tolerância.

## Resultado

```
Container gvenzl/oracle-free:23-slim started in PT54.0429562S
jdbc:oracle:thin:@localhost:57321/freepdb1

HikariPool-1 - Added connection ...
HikariPool-1 - Start completed.

Successfully validated 4 migrations
1 - create event table
2 - create sector table
3 - create reservation table
4 - add reservation transition timestamps
Successfully applied 4 migrations
...now at version v4

1 test passed, 1 test total
deveVenderNoMaximoACapacidadeDoSetorSobConcorrencia ✅
```

## O que fica como regra para o próximo problema parecido

| Camada | O que verificar | Comando / sinal |
|---|---|---|
| 1. Docker | O daemon está acessível pelo CLI? | `docker version`, `docker ps` |
| 2. Endpoint | O contexto ativo é o certo? Existe `DOCKER_HOST` indevido? | `docker context ls` |
| 3. Compatibilidade | As versões de Docker Engine, Testcontainers e docker-java são compatíveis entre si? | Comparar versões antes de mexer na aplicação |
| 4. Container específico | Depois que Docker + Testcontainers conversam bem, uma falha nova é do *container*, não do ambiente | Ler o log de inicialização do container, não assumir |
| 5. Timeout | O container inicia, produz logs, avança, mas não chega a "ready"? | `withStartupTimeout(...)` antes de trocar imagem, versão ou configuração |

A lição principal: **cada uma dessas três falhas tinha uma causa raiz diferente**, mesmo parecendo,
à primeira vista, "o mesmo erro de Docker". Diagnosticar em camadas — confirmando cada nível antes
de seguir para o próximo — evitou trocar peças do sistema (imagem, versão, configuração da
aplicação) às cegas até algo funcionar.
