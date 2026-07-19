# Servidor de Instituição (contas) com REST + Raft

Servidor de uma **instituição bancária**: cria e guarda **contas** (CPF, nome, senha, saldo e
extrato) e faz **transferências** entre contas — inclusive de **outras instituições**, por
chave Pix, com atomicidade garantida por um **commit em duas fases (2PC)** coordenado pelo
[banco-central](../banco-central/). Os clientes conversam por **REST** (Spring Boot); por
baixo, os servidores formam um **cluster replicado com consenso Raft** (via
[Apache Ratis](https://ratis.apache.org/)), de modo que tudo que é escrito em qualquer nó é
replicado, sobrevive à queda de nós e persiste em disco.

---

## Arquitetura

Camadas de rede independentes:

- **REST / HTTP** (externo) — API dos clientes: criar conta, logar, consultar saldo/extrato,
  gerenciar chaves e favoritos e **transferir**.
- **Raft / gRPC** (interno) — consenso e replicação **entre** os nós da instituição.
- **RMI** — cliente do **servidor-de-chaves** (chaves Pix) e do **banco-central** (roteamento
  e coordenação das transferências).

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
2. O `ContaController` valida CPF, nome e senha, **gera o número da conta** (se não informado),
   **calcula o hash BCrypt da senha** e sorteia o **saldo inicial**.
3. Monta um `ComandoCriarConta` determinístico e o entrega ao `AplicadorRaft`, que submete
   pelo `RaftClient` — que **acha o líder sozinho** (mesmo que o nó seja um seguidor).
4. O líder grava no log e **replica para a maioria** (2 de 3).
5. Após o commit, a `InstituicaoStateMachine` de **cada** nó aplica o comando no seu
   `BancoDeDados`.
6. O `POST` só retorna depois disso — quando o cliente recebe `201`, a conta **já está replicada**.

> **Determinismo:** número da conta, hash da senha e saldo inicial são resolvidos **antes** de
> o comando entrar no log. A StateMachine nunca gera valores novos — só reaplica o que está
> registrado —, garantindo que todos os nós cheguem ao mesmo estado. (BCrypt usa salt aleatório:
> hashear dentro da StateMachine faria cada nó divergir.)

### Como uma leitura flui (saldo/extrato)

As consultas são servidas do `BancoDeDados` **replicado local** de cada nó (rápidas). Como todo
nó aplica as entradas commitadas, a conta criada em um nó fica visível nos outros.

### Login e autenticação

- `POST /sessoes` valida a senha (BCrypt) e devolve um **token** de sessão.
- Saldo, extrato, transferência, chaves e favoritos exigem o token em `Authorization: Bearer <token>`.
- As sessões ficam **em memória por nó** (sticky) — os *dados* das contas é que são replicados.
  Continue falando com o mesmo nó em que você fez login.

### Como uma transferência flui (2PC entre instituições)

Uma transferência envolve **duas contas em bancos possivelmente diferentes** — e cada banco é
um cluster Raft independente. Quem garante o "tudo ou nada" é o [banco-central](../banco-central/),
coordenando um **commit em duas fases (2PC)**:

1. O cliente resolve o destino: `GET /destino?chave=...` (ou `?instituicao=&conta=`). A
   instituição pergunta ao **servidor-de-chaves** de quem é a chave e ao **banco-central** onde
   essa conta está.
2. O cliente chama `POST /contas/{origem}/transferir`. A instituição repassa ao banco-central
   (`solicitaTransacao`).
3. **PREPARE** — o banco-central pede às duas instituições que reservem a operação. Cada uma
   grava um `ComandoPreparar` **pelo Raft** (a movimentação fica pendente) e vota.
4. **Decisão** — se as duas prepararam, a decisão é `COMMIT`; senão, `ABORT`. Ela é **replicada
   pelo Raft do próprio banco-central**, então sobrevive à queda de nós dele.
5. **COMMIT/ABORT** — o banco-central manda cada instituição efetivar (`ComandoComitar`) ou
   desfazer (`ComandoCancelar`), de novo via Raft. Se um participante estava fora, o líder do
   banco-central **reenvia a decisão pendente** quando ele volta.

Resultado: o `POST` devolve `200` só quando as duas pontas confirmaram. Em qualquer falha,
**nenhum valor sai da conta de origem** — o saldo é conservado.

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
│   ├── bancocentral/               #   cliente RMI do banco-central (transferência + destino)
│   ├── pubsub/                     #   registro no banco-central + publicação do líder
│   └── web/                        #   REST
│       ├── ContaController.java    #     POST /contas, GET saldo/extrato, POST .../transferir
│       ├── AutenticacaoController  #     POST /sessoes (login)
│       ├── DestinoController.java  #     GET /destino (resolve chave/conta -> instituição)
│       ├── ChaveController.java    #     chaves da conta: registrar, portar, listar, existe
│       ├── FavoritoController.java #     favoritos de transferência da conta
│       └── dto/                    #     records de request/response
├── rmi/                            # contratos RMI: chaves (do servidor-de-chaves) + Transacao (2PC)
│   └── services/TransacaoService   #   participante do 2PC (prepare/commit/cancel)
├── raft/                           # integração com Apache Ratis
│   ├── NoInstituicao.java          #   bootstrap do nó (RaftServer + RaftClient + banco)
│   ├── ClusterConfig.java          #   peers, portas, id do grupo e storage
│   ├── InstituicaoStateMachine.java#   máquina de estados replicada (aplica no banco + snapshots)
│   ├── ComandoCriarConta.java      #   comandos serializáveis que viajam no log Raft…
│   ├── ComandoPreparar/Comitar/Cancelar.java  #   …as fases do 2PC de transferência
│   ├── ComandoAdicionar/RemoverFavorito.java  #   …e os favoritos
│   └── Aplicador{DeContas,Local,Raft}.java     #   abstração da escrita (local vs. Raft)
└── estruturas/                     # domínio
    ├── CPF.java, conta/, financeiro/Saldo.java, transacao/, instituicao/
    └── db/                         #   BancoDeDados (mapa numeroConta -> conta) + exceptions
```

---

## Pré-requisitos

- **Docker** + **Docker Compose** — a execução é sempre pelo compose (ver [Execução](#execução)).
- **Java 21** (JDK) — apenas para rodar a suíte de testes; o Maven Wrapper (`mvnw` / `mvnw.cmd`) já vem incluso.

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

A suíte cobre os testes REST ponta a ponta (`ContaIntegrationTest`), o 2PC de transferência
(`TransferenciaIntegracaoTest`) e cenários de falha do cluster durante a transferência
(`TransferenciaCaosRaftTest`, `InstituicaoToleranciaFalhaTest`).

---

## Execução

A execução é orquestrada pelo **`docker compose` da raiz do repositório**, que sobe as
**três instituições** (Meridiano/Aurora/Vetor), cada uma um cluster Raft de 3 nós, junto
com o servidor de chaves e o banco central na rede `dsid`:

```bash
# na raiz do repositório
docker compose up --build
docker compose down            # derruba (volumes preservam os dados)
docker compose down -v         # derruba e apaga os dados
```

Cada instituição publica o HTTP do seu `n1` no host:

| Instituição | Serviços | HTTP no host |
|-------------|----------|--------------|
| Meridiano (INST-0001) | meridiano-n1/n2/n3 | `8081` |
| Aurora (INST-0002) | aurora-n1/n2/n3 | `8082` |
| Vetor (INST-0003) | vetor-n1/n2/n3 | `8083` |

- Os nós de cada instituição se acham pelo nome do serviço (`RAFT_HOST_*`), apontam para o
  servidor de chaves via `CHAVES_HOST`/`CHAVES_PORT` e para o banco central via
  `BC_HOST`/`BC_HOSTS`/`BC_PORT` (variáveis de ambiente do compose).
- O Raft precisa de maioria (2 de 3) para aceitar escritas; as sessões de login são por nó
  (sticky), então continue falando com o mesmo nó publicado.
- Para acessar essas instituições pela interface desktop, veja o
  [interface-instituicao](../interface-instituicao/README.md).

> Passo a passo completo (backend + interfaces) no [README da raiz](../README.md).

---

## API REST

Respostas de erro têm o formato `{ "erro": "..." }`.

### Criar conta — `POST /contas`

```jsonc
// corpo
{ "cpf": "529.982.247-25", "nome": "Ana", "senha": "segredo123", "numeroConta": "12345-6" }
// numeroConta é opcional; se ausente, o servidor gera um. A conta nasce com um saldo inicial.
```

| Status | Quando |
|--------|--------|
| `201 Created` | criada — `{ "numeroConta": "...", "cpf": "...", "nome": "..." }` |
| `400 Bad Request` | CPF inválido / faltou CPF, nome ou senha |
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

`200` → `{ "numeroConta": "...", "cpf": "...", "nome": "..." }` · `404` se não existe.

### Saldo — `GET /contas/{numero}/saldo`  *(exige token)*

`200` → `{ "numeroConta": "...", "saldoCentavos": 90337 }` (saldo real, em centavos) · `401` sem token válido desta conta.

### Extrato — `GET /contas/{numero}/extrato`  *(exige token)*

`200` → `{ "numeroConta": "...", "transacoes": [ { "id": "...", "contaOrigem": "...",
"contaDestino": "...", "valorCentavos": 500, "horaTransacao": "..." } ] }` · `401` sem token válido.

### Resolver destino — `GET /destino`

Descobre para quem vai a transferência, por **chave Pix** ou por **instituição + conta**:

```
GET /destino?chave=ana@banco.com
GET /destino?instituicao=INST-0002&conta=22781403
```

`200` → `{ "idInstituicao": "...", "numeroConta": "...", "nome": "..." }` · `404` não encontrado ·
`502` servidor de chaves / banco central fora.

### Transferir — `POST /contas/{numero}/transferir`  *(exige token)*

```jsonc
{ "idInstituicaoDestino": "INST-0002", "contaDestino": "22781403", "valorCentavos": 500 }
```

Coordenada pelo banco-central em 2PC (ver
[Como uma transferência flui](#como-uma-transferência-flui-2pc-entre-instituições)).

| Status | Quando |
|--------|--------|
| `200 OK` | concluída — débito na origem e crédito no destino efetivados |
| `500` | recusada (saldo insuficiente, destino inválido, sem maioria em um dos lados) |
| `502 Bad Gateway` | banco-central indisponível — **nada saiu da conta de origem** |

### Registrar chave — `POST /contas/{numero}/chaves`  *(exige token)*

Registra uma chave no **servidor-de-chaves** apontando para esta conta.

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

### Trocar chave de instituição — `PUT /contas/{numero}/chaves`  *(exige token)*

Portabilidade: uma chave **já registrada** (possivelmente de outra instituição/conta) passa a
apontar para esta conta. Usado quando o `POST` devolve `409` e o usuário confirma que quer trazê-la.

```jsonc
{ "tipo": "CPF", "valor": "529.982.247-25" }
// tipo: CPF | TELEFONE | EMAIL (ALEATORIA não se troca)
```

| Status | Quando |
|--------|--------|
| `200 OK` | chave agora aponta para esta conta |
| `400 Bad Request` | tipo inválido / valor faltando |
| `401 Unauthorized` | sem token válido desta conta |
| `502 Bad Gateway` | servidor de chaves indisponível/recusou |

### Listar chaves da conta — `GET /contas/{numero}/chaves`  *(exige token)*

`200` → `{ "numeroConta": "...", "chaves": [ "ana@banco.com", ... ] }` · `401` · `502` servidor de chaves fora.

### Consultar existência de chave — `GET /chaves/{valor}/existe`

`200` → `{ "valor": "...", "existe": true|false }` · `502` se o servidor de chaves está fora.

### Favoritos de transferência — `/contas/{numero}/favoritos`  *(exige token)*

Destinos salvos com apelido, replicados pelo Raft da instituição.

- `GET` → `{ "numeroConta": "...", "favoritos": [ ... ] }`
- `POST` `{ "apelido": "Bruno", "idInstituicao": "INST-0002", "numeroConta": "22781403", "chave": "...", "nome": "..." }` → `201`
- `DELETE /{id}` → `204`

### Exemplo de fluxo (curl / Git Bash)

```bash
BASE=http://localhost:8081

# 1) criar conta (nome é obrigatório)
curl -s -X POST $BASE/contas -H 'Content-Type: application/json' \
  -d '{"cpf":"529.982.247-25","nome":"Ana","senha":"segredo123","numeroConta":"12345-6"}'

# 2) login -> pega o token
TOKEN=$(curl -s -X POST $BASE/sessoes -H 'Content-Type: application/json' \
  -d '{"numeroConta":"12345-6","senha":"segredo123"}' | jq -r .token)

# 3) ver saldo e extrato autenticado
curl -s $BASE/contas/12345-6/saldo   -H "Authorization: Bearer $TOKEN"
curl -s $BASE/contas/12345-6/extrato -H "Authorization: Bearer $TOKEN"

# 4) transferir R$5 para uma conta do Aurora (INST-0002)
curl -s -X POST $BASE/contas/12345-6/transferir -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"idInstituicaoDestino":"INST-0002","contaDestino":"22781403","valorCentavos":500}'
```

---

## Modo de execução (config)

| Propriedade | Padrão | Efeito |
|-------------|--------|--------|
| `instituicao.node-id` | `n1` | id do nó no cluster Raft (`n1`/`n2`/`n3`) |
| `server.port` | `8081` | porta HTTP |
| `instituicao.raft.enabled` | `true` | `false` = modo local (sem consenso) |
| `instituicao.id` | `INST-0001` | identificador desta instituição |
| `chaves.host` / `chaves.port` | `127.0.0.1` / `1099` | endereço RMI do servidor-de-chaves |
| `bc.hosts` / `bc.port` | `127.0.0.1` / `1200` | endereços RMI do banco-central |

Os profiles `n1`/`n2`/`n3` (`application-<id>.properties`) só ajustam `node-id` e `server.port`.
No Docker, `RAFT_PORT`, `RAFT_HOST_*`, `CHAVES_*` e `BC_*` (variáveis de ambiente) sobrescrevem
os padrões.

---

## Integração com os outros serviços

A instituição é **cliente RMI** de dois serviços:

- **servidor-de-chaves** — o [`ClienteServidorChaves`](src/main/java/instituicao/chaves/ClienteServidorChaves.java)
  registra, porta, lista e consulta chaves Pix. Registrar chave é um passo separado de criar
  conta: se o servidor de chaves estiver fora, criar contas continua funcionando e o registro
  responde `502`.
- **banco-central** — o [`ClienteBancoCentral`](src/main/java/instituicao/bancocentral/ClienteBancoCentral.java)
  resolve o destino (`ConsultaDestino`) e solicita a transferência (`Transferencia`). A
  instituição também **se registra** no banco-central e **publica quem é seu líder** (pubsub),
  e expõe o serviço RMI `Transacao` como **participante do 2PC**.

```
  Cliente ──REST──▶ instituicao ──RMI──▶ servidor-de-chaves   (chave -> conta)
                        │        ──RMI──▶ banco-central        (destino + 2PC da transferência)
```

O contrato RMI das chaves (`rmi.RegistroChaveInterface` / `rmi.ConsultaChaveInterface`) é uma
cópia das interfaces do outro projeto — o cliente RMI precisa das mesmas classes.

---

## Dependências principais

| Artefato | Papel |
|----------|-------|
| `spring-boot-starter-web` | REST (Tomcat embutido + Jackson) |
| `spring-security-crypto` | BCrypt para o hash de senha (sem o security inteiro) |
| `ratis-server` / `-client` / `-grpc` | consenso Raft (motor, cliente, transporte) |
| `ratis-metrics-default` | métricas do Ratis (runtime) |

---

## Limitações conhecidas

- **Sessões não replicadas** — o login fica em memória no nó (sticky); depois de um restart do
  nó o token expira e é preciso logar de novo. Os *dados* das contas é que são replicados e
  persistidos. Para remover a afinidade, dá para assinar tokens (ex.: JWT) ou usar um store
  compartilhado.
