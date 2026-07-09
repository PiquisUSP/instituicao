package instituicao.web;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import estruturas.CPF;
import estruturas.conta.ContaBancaria;
import estruturas.db.BancoDeDados;
import instituicao.seguranca.SessaoService;
import instituicao.web.dto.ContaResponse;
import instituicao.web.dto.CriarContaRequest;
import instituicao.web.dto.ErroResponse;
import instituicao.web.dto.ExtratoResponse;
import instituicao.web.dto.SaldoResponse;
import instituicao.web.dto.TransacaoResponse;
import raft.AplicadorDeContas;
import raft.ComandoCriarConta;

// API REST de contas. POST valida, hasheia a senha, monta o comando e entrega ao
// aplicador (Raft ou local). Saldo e extrato exigem login (Bearer token).
@RestController
@RequestMapping("/contas")
public class ContaController {

    private static final Logger log = LoggerFactory.getLogger(ContaController.class);

    private final AplicadorDeContas aplicador;
    private final BancoDeDados banco;
    private final PasswordEncoder encoder;
    private final SessaoService sessoes;

    public ContaController(AplicadorDeContas aplicador, BancoDeDados banco,
                           PasswordEncoder encoder, SessaoService sessoes) {
        this.aplicador = aplicador;
        this.banco = banco;
        this.encoder = encoder;
        this.sessoes = sessoes;
    }

    @PostMapping
    public ResponseEntity<?> criar(@RequestBody CriarContaRequest req) {
        log.info("[CONTA] POST /contas recebido (cpf={})", req == null ? null : req.cpf());

        if (req == null || req.cpf() == null) {
            log.warn("[CONTA] -> 400: CPF ausente");
            return ResponseEntity.badRequest().body(new ErroResponse("CPF é obrigatório"));
        }
        if (req.senha() == null || req.senha().isBlank()) {
            log.warn("[CONTA] -> 400: senha ausente");
            return ResponseEntity.badRequest().body(new ErroResponse("Senha é obrigatória"));
        }
        if (req.nome() == null || req.nome().isBlank()) {
            log.warn("[CONTA] -> 400: nome ausente");
            return ResponseEntity.badRequest().body(new ErroResponse("Nome é obrigatório"));
        }

        // O construtor só preenche o valor se o CPF for válido.
        CPF cpf = new CPF(req.cpf());
        if (cpf.getValor() == null) {
            log.warn("[CONTA] -> 400: CPF inválido ({})", req.cpf());
            return ResponseEntity.badRequest().body(new ErroResponse("CPF inválido: " + req.cpf()));
        }

        // Número e hash resolvidos aqui, antes do Raft, para todos os nós aplicarem o mesmo.
        boolean numeroGerado = req.numeroConta() == null || req.numeroConta().isBlank();
        String numero = numeroGerado ? gerarNumeroConta() : req.numeroConta().trim();
        String senhaHash = encoder.encode(req.senha());
        log.info("[CONTA] validado; numeroConta={} ({}); senha hasheada; submetendo escrita...",
                numero, numeroGerado ? "gerado" : "informado");

        int status = aplicador.registrar(new ComandoCriarConta(numero, cpf.getValor(), req.nome().trim(), senhaHash));

        return switch (status) {
            case 200 -> {
                log.info("[CONTA] -> 201 CRIADA numeroConta={}", numero);
                yield ResponseEntity.status(HttpStatus.CREATED)
                        .body(new ContaResponse(numero, cpf.getValor(), req.nome().trim()));
            }
            case 403 -> {
                log.warn("[CONTA] -> 409 já existe numeroConta={}", numero);
                yield ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ErroResponse("Conta já registrada: " + numero));
            }
            default -> {
                log.error("[CONTA] -> 503 falha ao replicar (status interno={})", status);
                yield ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new ErroResponse("Não foi possível replicar via Raft (há maioria de nós no ar?)"));
            }
        };
    }

    // Dados públicos da conta (sem saldo/extrato).
    @GetMapping("/{numero}")
    public ResponseEntity<?> consultar(@PathVariable String numero) {
        log.info("[CONTA] GET /contas/{}", numero);
        ContaBancaria conta = banco.recuperarConta(numero);
        if (conta == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new ContaResponse(
                conta.getNumeroConta().getValor(), conta.getCpf().getValor(), conta.getNome()));
    }

    @GetMapping("/{numero}/saldo")
    public ResponseEntity<?> saldo(@PathVariable String numero,
                                   @RequestHeader(value = "Authorization", required = false) String authorization) {
        log.info("[SALDO] GET /contas/{}/saldo", numero);
        ResponseEntity<?> negado = exigirAutenticacao(numero, authorization);
        if (negado != null) {
            return negado;
        }
        ContaBancaria conta = banco.recuperarConta(numero);
        if (conta == null) {
            return ResponseEntity.notFound().build();
        }
        long saldo = conta.getSaldo().getValor();
        log.info("[SALDO] numeroConta={} -> {} centavos", numero, saldo);
        return ResponseEntity.ok(new SaldoResponse(numero, saldo));
    }

    @GetMapping("/{numero}/extrato")
    public ResponseEntity<?> extrato(@PathVariable String numero,
                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
        log.info("[EXTRATO] GET /contas/{}/extrato", numero);
        ResponseEntity<?> negado = exigirAutenticacao(numero, authorization);
        if (negado != null) {
            return negado;
        }
        ContaBancaria conta = banco.recuperarConta(numero);
        if (conta == null) {
            return ResponseEntity.notFound().build();
        }
        List<TransacaoResponse> transacoes = conta.getExtrato().puxarExtrato().stream()
                .map(t -> new TransacaoResponse(
                        t.getId() != null ? t.getId().toString() : null,
                        t.getContaBancariaOrigem() != null ? t.getContaBancariaOrigem().getValor() : null,
                        t.getContaBancariaDestino() != null ? t.getContaBancariaDestino().getValor() : null,
                        t.getValorTransacaoCentavos(),
                        t.getHoraTransacao() != null ? t.getHoraTransacao().toString() : null))
                .toList();
        log.info("[EXTRATO] numeroConta={} -> {} transação(ões)", numero, transacoes.size());
        return ResponseEntity.ok(new ExtratoResponse(numero, transacoes));
    }

    // 401 se o token não autenticar esta conta; null se ok.
    private ResponseEntity<?> exigirAutenticacao(String numero, String authorization) {
        if (!sessoes.autorizadoHeader(authorization, numero)) {
            log.warn("[AUTH] -> 401 acesso negado à conta {} (token ausente/inválido)", numero);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErroResponse("Não autenticado para esta conta"));
        }
        return null;
    }

    private static String gerarNumeroConta() {
        long n = ThreadLocalRandom.current().nextLong(1, 100_000_000L);
        return String.format("%08d", n);
    }
}
