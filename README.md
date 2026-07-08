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
│   └── web/                        #   REST
│       ├── ContaController.java    #     POST /contas, GET saldo/extrato
│       ├── AutenticacaoController  #     POST /sessoes (login)
│       └── dto/                    #     records de request/response
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
```

---

## Modo de execução (config)

| Propriedade | Padrão | Efeito |
|-------------|--------|--------|
| `instituicao.node-id` | `n1` | id do nó no cluster Raft (`n1`/`n2`/`n3`) |
| `server.port` | `8081` | porta HTTP |
| `instituicao.raft.enabled` | `true` | `false` = modo local (sem consenso) |

Os profiles `n1`/`n2`/`n3` (`application-<id>.properties`) só ajustam `node-id` e `server.port`.

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
- **Docker / Kubernetes** — empacotar como o `servidor-de-chaves` (as variáveis `RAFT_HOST_*`,
  `RAFT_PORT` já são lidas pelo `ClusterConfig`).
