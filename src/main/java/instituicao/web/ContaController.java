package instituicao.web;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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

/**
 * API REST de contas.
 *
 * <p>Escritas ({@code POST}) são validadas, a senha é hasheada (BCrypt) e o
 * resultado vira um {@link ComandoCriarConta} determinístico entregue ao
 * {@link AplicadorDeContas} — que replica via Raft ou aplica localmente.
 *
 * <p>Saldo e extrato exigem autenticação: o cliente faz login em {@code /sessoes}
 * e apresenta o token em {@code Authorization: Bearer <token>}.
 */
@RestController
@RequestMapping("/contas")
public class ContaController {

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

    /** Cria uma conta. Exige CPF e senha; o número pode ser informado ou gerado. */
    @PostMapping
    public ResponseEntity<?> criar(@RequestBody CriarContaRequest req) {
        if (req == null || req.cpf() == null) {
            return ResponseEntity.badRequest().body(new ErroResponse("CPF é obrigatório"));
        }
        if (req.senha() == null || req.senha().isBlank()) {
            return ResponseEntity.badRequest().body(new ErroResponse("Senha é obrigatória"));
        }

        // Valida o CPF: o construtor só preenche o valor se for válido.
        CPF cpf = new CPF(req.cpf());
        if (cpf.getValor() == null) {
            return ResponseEntity.badRequest().body(new ErroResponse("CPF inválido: " + req.cpf()));
        }

        // Número e hash da senha são resolvidos AQUI (antes de entrar no log Raft)
        // para todos os nós aplicarem exatamente os mesmos valores — determinismo.
        String numero = (req.numeroConta() != null && !req.numeroConta().isBlank())
                ? req.numeroConta().trim()
                : gerarNumeroConta();
        String senhaHash = encoder.encode(req.senha());

        int status = aplicador.registrar(new ComandoCriarConta(numero, cpf.getValor(), senhaHash));

        return switch (status) {
            case 200 -> ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ContaResponse(numero, cpf.getValor()));
            case 403 -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErroResponse("Conta já registrada: " + numero));
            default -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErroResponse("Não foi possível replicar via Raft (há maioria de nós no ar?)"));
        };
    }

    /** Dados públicos da conta (sem saldo/extrato). */
    @GetMapping("/{numero}")
    public ResponseEntity<?> consultar(@PathVariable String numero) {
        ContaBancaria conta = banco.recuperarConta(numero);
        if (conta == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new ContaResponse(
                conta.getNumeroConta().getValor(), conta.getCpf().getValor()));
    }

    /** Saldo da conta (exige login desta conta). */
    @GetMapping("/{numero}/saldo")
    public ResponseEntity<?> saldo(@PathVariable String numero,
                                   @RequestHeader(value = "Authorization", required = false) String authorization) {
        ResponseEntity<?> negado = exigirAutenticacao(numero, authorization);
        if (negado != null) {
            return negado;
        }
        ContaBancaria conta = banco.recuperarConta(numero);
        if (conta == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new SaldoResponse(numero, conta.getSaldo().getValor()));
    }

    /** Extrato da conta (exige login desta conta). */
    @GetMapping("/{numero}/extrato")
    public ResponseEntity<?> extrato(@PathVariable String numero,
                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
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
        return ResponseEntity.ok(new ExtratoResponse(numero, transacoes));
    }

    /** Retorna 401 se o token não autenticar esta conta; null se estiver ok. */
    private ResponseEntity<?> exigirAutenticacao(String numero, String authorization) {
        if (!sessoes.autorizadoHeader(authorization, numero)) {
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
