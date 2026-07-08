# Servidor de Instituição (contas) com REST + Raft

Servidor de uma **instituição bancária**: cria e guarda **contas** (CPF, senha, saldo e
extrato). Os clientes conversam com o servidor por **REST** (Spring Boot); por baixo, os
servidores formam um **cluster replicado com consenso Raft** (via
[Apache Ratis](https://ratis.apache.org/)), de modo que uma conta criada em qualquer nó é
replicada, sobrevive à queda de nós e persiste em disco.

---

## Arquitetura

Duas camadas de rede independentes:

- **REST / HTTP** (externo) — API usada pelos clientes para criar conta, logar e consultar.
- **Raft / gRPC** (interno) — consenso e replicação **entre** os servidores.

```
                        Clientes (REST / HTTP)
                              │
              ┌───────────────┼───────────────┐
              │ HTTP          │ HTTP          │ HTTP
              ▼               ▼               ▼
         ┌──────────┐    ┌──────────┐    ┌──────────┐
         │    n1    │    │    n2    │    │    n3    │
         │ HTTP 8081│    │ HTTP 8082│    │ HTTP 8083│
         │ Raft 7001│◀──▶│ Raft 7002│◀──▶│ Raft 7003│
         └──────────┘    └──────────┘    └──────────┘
              ▲                                │
              └──────── consenso Raft (gRPC) ──┘
              (replicação do log + eleição de líder)
```

### Como uma escrita flui (criar conta)

1. O cliente chama `POST /contas` em **qualquer** nó.
2. O `ContaController` valida CPF e senha, **gera o número da conta** (se não informado) e
   **calcula o hash BCrypt da senha**.
3. Monta um `ComandoCriarConta` determinístico e o entrega ao `AplicadorRaft`, que submete
   pelo `RaftClient` — que **acha o líder sozinho** (mesmo que o nó seja um seguidor).
4. O líder grava no log e **replica para a maioria** (2 de 3).
5. Após o commit, a `InstituicaoStateMachine` de **cada** nó aplica o comando no seu
   `BancoDeDados`.
6. O `POST` só retorna depois disso — quando o cliente recebe `201`, a conta **já está replicada**.

> **Determinismo:** número da conta e hash da senha são resolvidos **antes** de o comando
> entrar no log. A StateMachine nunca gera valores novos — só reaplica o que está registrado —,
> garantindo que todos os nós cheguem ao mesmo estado. (BCrypt usa salt aleatório: hashear
> dentro da StateMachine faria cada nó divergir.)

### Como uma leitura flui (saldo/extrato)

As consultas são servidas do `BancoDeDados` **replicado local** de cada nó (rápidas). Como todo
nó aplica as entradas commitadas, a conta criada em um nó fica visível nos outros.

### Login e autenticação

- `POST /sessoes` valida a senha (BCrypt) e devolve um **token** de sessão.
- Saldo e extrato exigem o token em `Authorization: Bearer <token>`.
- As sessões ficam **em memória por nó** (sticky) — os *dados* das contas é que são replicados.
  Continue falando com o mesmo nó em que você fez login.

---

## Persistência em disco

O estado sobrevive à queda/reinício dos nós:

- **Log do Raft** — cada entrada é gravada em `raft-storage/<id>/` antes do commit (o Ratis faz isso).
- **Snapshots da StateMachine** — a cada 10 entradas aplicadas, `takeSnapshot()` grava o estado
  do `BancoDeDados` em disco (serializado) e o log pode ser compactado.
- **Recuperação no restart** — ao subir, o nó **recupera** (`RECOVER`) o estado do snapshot mais
  recente e reproduz o resto do log; na primeira vez, **formata** (`FORMAT`).

`raft-storage/` é ignorado pelo Git; apagar essa pasta zera o estado do cluster.

---

## Estrutura do projeto

```
src/main/java/
├── instituicao/                    # camada Spring (componentes escaneados)
│   ├── Application.java            #   @SpringBootApplication (ponto de entrada)
│   ├── config/
│   │   ├── RaftModeConfig.java     #   modo replicado: sobe o nó Raft (padrão)
│   │   ├── LocalModeConfig.java    #   modo local: sem consenso (dev/teste)
│   │   └── SegurancaConfig.java    #   PasswordEncoder (BCrypt)
│   ├── seguranca/SessaoService.java#   sessões (token -> conta) em memória
│   ├── chaves/                     #   cliente RMI do servidor-de-chaves
│   │   ├── ClienteServidorChaves.java #  registra/consulta chaves via RMI
│   │   └── TipoChave.java / ServidorChavesIndisponivel.java
│   └── web/                        #   REST
│       ├── ContaController.java    #     POST /contas, GET saldo/extrato
│       ├── AutenticacaoController  #     POST /sessoes (login)
│       ├── ChaveController.java    #     POST /contas/{n}/chaves, GET /chaves/{v}/existe
│       └── dto/                    #     records de request/response
├── rmi/                            # contrato RMI copiado do servidor-de-chaves (interfaces)
├── raft/                           # integração com Apache Ratis
│   ├── NoInstituicao.java          #   bootstrap do nó (RaftServer + RaftClient + banco)
│   ├── ClusterConfig.java          #   peers, portas, id do grupo e storage
│   ├── InstituicaoStateMachine.java#   máquina de estados replicada (aplica no banco + snapshots)
│   ├── ComandoCriarConta.java      #   comando serializável que viaja no log Raft
│   ├── AplicadorDeContas.java      #   abstração da escrita (local vs. Raft)
│   ├── AplicadorLocal.java         #   escrita direta (sem replicação)
│   └── AplicadorRaft.java          #   escrita via consenso (RaftClient)
└── estruturas/                     # domínio
    ├── CPF.java, conta/, financeiro/Saldo.java, instituicao/
    └── db/                         #   BancoDeDados (mapa numeroConta -> conta) + exceptions
```

---

## Pré-requisitos

- **Java 21** (JDK)
- Maven Wrapper incluso (`mvnw` / `mvnw.cmd`) — não precisa instalar o Maven

---

## Compilar e testar

```powershell
# Windows (PowerShell)
.\mvnw.cmd test
```

```bash
# Linux / macOS / Git Bash
./mvnw test
```

A suíte inclui os testes de integração REST ponta a ponta (`ContaIntegrationTest`), que rodam
em **modo local** (sem precisar de cluster).

---

## Rodar

### Modo local (processo único, sem consenso)

Para desenvolver/testar a API sem precisar de maioria de nós:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--instituicao.raft.enabled=false"
```

Sobe na porta **8081** usando o `AplicadorLocal` (banco em memória, sem replicação).

### Cluster Raft (3 nós)

Cada nó é um processo. Abra **um terminal por nó** e ative o profile correspondente:

```powershell
# Terminal 1
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=n1"
# Terminal 2
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=n2"
# Terminal 3
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=n3"
```

> **Suba pelo menos 2 nós** (maioria de 3). Com só 1 nó no ar não há líder e as escritas
> ficam bloqueadas — é o Raft priorizando consistência. (Para 1 processo só, use o modo local.)

#### Portas por nó

| Nó  | HTTP | Raft (gRPC) |
|-----|------|-------------|
| n1  | 8081 | 7001        |
| n2  | 8082 | 7002        |
| n3  | 8083 | 7003        |

As portas Raft (7001-7003) são diferentes das do `servidor-de-chaves` (6001-6003), então os
dois clusters podem rodar na mesma máquina.

---

## API REST

Respostas de erro têm o formato `{ "erro": "..." }`.

### Criar conta — `POST /contas`

```jsonc
// corpo
{ "cpf": "529.982.247-25", "senha": "segredo123", "numeroConta": "12345-6" }
// numeroConta é opcional; se ausente, o servidor gera um
```

| Status | Quando |
|--------|--------|
| `201 Created` | criada — `{ "numeroConta": "...", "cpf": "..." }` |
| `400 Bad Request` | CPF inválido / faltou CPF ou senha |
| `409 Conflict` | número de conta já existe |
| `503 Service Unavailable` | Raft sem maioria de nós |

### Login — `POST /sessoes`

```jsonc
{ "numeroConta": "12345-6", "senha": "segredo123" }
```

| Status | Quando |
|--------|--------|
| `200 OK` | `{ "token": "...", "numeroConta": "..." }` |
| `401 Unauthorized` | credenciais inválidas |

### Consultar conta (público) — `GET /contas/{numero}`

`200` → `{ "numeroConta": "...", "cpf": "..." }` · `404` se não existe.

### Saldo — `GET /contas/{numero}/saldo`  *(exige token)*

`200` → `{ "numeroConta": "...", "saldoCentavos": 0 }` · `401` sem token válido desta conta.

### Extrato — `GET /contas/{numero}/extrato`  *(exige token)*

`200` → `{ "numeroConta": "...", "transacoes": [ ... ] }` · `401` sem token válido desta conta.

### Registrar chave — `POST /contas/{numero}/chaves`  *(exige token)*

Registra uma chave no **servidor-de-chaves** apontando para esta conta (ver
[Integração](#integração-com-o-servidor-de-chaves)).

```jsonc
{ "tipo": "CPF", "valor": "529.982.247-25" }
// tipo: CPF | TELEFONE | EMAIL | ALEATORIA (valor dispensado para ALEATORIA)
```

| Status | Quando |
|--------|--------|
| `201 Created` | chave registrada — `{ "tipo": "...", "valor": "...", "numeroConta": "..." }` |
| `400 Bad Request` | tipo inválido / valor faltando |
| `401 Unauthorized` | sem token válido desta conta |
| `409 Conflict` | chave já registrada no servidor de chaves |
| `502 Bad Gateway` | servidor de chaves indisponível/recusou |

### Consultar existência de chave — `GET /chaves/{valor}/existe`

`200` → `{ "valor": "...", "existe": true|false }` · `502` se o servidor de chaves está fora.

### Exemplo de fluxo (curl / Git Bash)

```bash
BASE=http://localhost:8081

# 1) criar conta
curl -s -X POST $BASE/contas -H 'Content-Type: application/json' \
  -d '{"cpf":"529.982.247-25","senha":"segredo123","numeroConta":"12345-6"}'

# 2) login -> pega o token
TOKEN=$(curl -s -X POST $BASE/sessoes -H 'Content-Type: application/json' \
  -d '{"numeroConta":"12345-6","senha":"segredo123"}' | jq -r .token)

# 3) ver saldo e extrato autenticado
curl -s $BASE/contas/12345-6/saldo   -H "Authorization: Bearer $TOKEN"
curl -s $BASE/contas/12345-6/extrato -H "Authorization: Bearer $TOKEN"

# 4) registrar uma chave (vai ao servidor-de-chaves por RMI)
curl -s -X POST $BASE/contas/12345-6/chaves -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"tipo":"CPF","valor":"529.982.247-25"}'
```

---

## Modo de execução (config)

| Propriedade | Padrão | Efeito |
|-------------|--------|--------|
| `instituicao.node-id` | `n1` | id do nó no cluster Raft (`n1`/`n2`/`n3`) |
| `server.port` | `8081` | porta HTTP |
| `instituicao.raft.enabled` | `true` | `false` = modo local (sem consenso) |
| `instituicao.id` | `INST-0001` | identificador desta instituição (usado ao registrar chaves) |
| `chaves.host` / `chaves.port` | `127.0.0.1` / `1099` | endereço RMI do servidor-de-chaves |

Os profiles `n1`/`n2`/`n3` (`application-<id>.properties`) só ajustam `node-id` e `server.port`.
Em Docker/K8s, `RAFT_PORT`, `RAFT_HOST_*`, `CHAVES_HOST` e `CHAVES_PORT` (variáveis de
ambiente) sobrescrevem os padrões.

---

## Integração com o servidor-de-chaves

A instituição é **cliente RMI** do `servidor-de-chaves`. O
[`ClienteServidorChaves`](src/main/java/instituicao/chaves/ClienteServidorChaves.java) faz o
lookup no registry RMI (`chaves.host:chaves.port`) e chama o serviço `RegistroChave`.

- **Contrato compartilhado:** as interfaces `rmi.RegistroChaveInterface` /
  `rmi.ConsultaChaveInterface` (+ `ServiceResult`) são cópias do contrato do outro projeto —
  o cliente RMI precisa das mesmas classes.
- **O que é usado:** `registrar*` (retorna `int`) e `existeChave` (retorna `boolean`) — só
  primitivos, sem DTO compartilhado frágil.
- **O que NÃO é usado:** `consultarChave`, porque devolveria um `ContaBancaria` no formato do
  outro projeto (`IdentificadorInstituicao` + `NumeroConta`), incompatível com o `ContaBancaria`
  desta instituição (mesmo nome, forma diferente → não desserializa).
- **Desacoplamento:** registrar chave é um passo separado de criar conta. Se o servidor de
  chaves estiver fora do ar, criar contas continua funcionando; o registro da chave responde `502`.

```
  Cliente ──REST──▶ instituicao ──RMI──▶ servidor-de-chaves
                    (contas)              (chave -> conta)
```

---

## Docker

```bash
# imagem só desta instituição
docker build -t instituicao .

# cluster completo (chaves + instituicao) interligado — a partir da RAIZ do workspace
cd ..            # d:/Projetos/dsid
docker compose up --build
```

O [`docker-compose.yml` da raiz](../docker-compose.yml) sobe **os dois clusters** na mesma rede:
3 nós do `servidor-de-chaves` (RMI 1099-1101) + 3 nós da `instituicao` (HTTP 8081-8083), com
`CHAVES_HOST=chaves-n1`. Há também um [`docker-compose.yml`](docker-compose.yml) local só da
instituição (rede externa `dsid`).

---

## Kubernetes

Manifests em [`k8s/`](k8s/), no mesmo modelo do `servidor-de-chaves`: **StatefulSet** (3 nós,
disco por nó), **headless Service** (DNS por pod, para o Raft), **LoadBalancer** (REST, com
`sessionAffinity: ClientIP` — as sessões de login são por nó) e **PodDisruptionBudget**.

```bash
docker build -t instituicao:latest .
#  minikube: minikube image load instituicao:latest

# O servidor-de-chaves precisa já estar implantado (a instituicao aponta para
# servidor-chaves-0.servidor-chaves-hl:1099 via CHAVES_HOST/CHAVES_PORT).
kubectl apply -f k8s/

kubectl get pods -l app=instituicao -w
```

- **pod → nó:** ordinal vira `n1/n2/n3`; `RAFT_HOST_*` = DNS de cada pod; `RAFT_PORT`/`server.port`
  uniformes (o Service balanceia).
- **Acesso ao REST:** `kubectl get svc instituicao-http` → IP do LoadBalancer (em minikube,
  `minikube tunnel`).

---

## Dependências principais

| Artefato | Papel |
|----------|-------|
| `spring-boot-starter-web` | REST (Tomcat embutido + Jackson) |
| `spring-security-crypto` | BCrypt para o hash de senha (sem o security inteiro) |
| `ratis-server` / `-client` / `-grpc` | consenso Raft (motor, cliente, transporte) |
| `ratis-metrics-default` | métricas do Ratis (runtime) |

---

## Pendente / próximos passos

- **Depósito e transferência** — ainda não há operação que movimente saldo ou gere transações,
  então **saldo é sempre 0 e o extrato fica vazio**. O caminho natural é criar comandos Raft
  (`ComandoDeposito`/`ComandoTransferencia`) aplicados pela StateMachine, mantendo o mesmo padrão
  determinístico das escritas.
- **Sessões replicadas** — hoje o login é por nó (sticky). Para não depender de afinidade, dá
  para replicar/expirar tokens (ex.: JWT assinado) ou um store compartilhado.
