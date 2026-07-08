package instituicao.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import estruturas.conta.ContaBancaria;
import estruturas.db.BancoDeDados;
import instituicao.seguranca.SessaoService;
import instituicao.web.dto.ErroResponse;
import instituicao.web.dto.LoginRequest;
import instituicao.web.dto.LoginResponse;

/**
 * Login. Verifica a senha (BCrypt) contra a conta e, se conferir, abre uma
 * sessão e devolve um token.
 */
@RestController
@RequestMapping("/sessoes")
public class AutenticacaoController {

    private static final Logger log = LoggerFactory.getLogger(AutenticacaoController.class);

    private final BancoDeDados banco;
    private final PasswordEncoder encoder;
    private final SessaoService sessoes;

    public AutenticacaoController(BancoDeDados banco, PasswordEncoder encoder, SessaoService sessoes) {
        this.banco = banco;
        this.encoder = encoder;
        this.sessoes = sessoes;
    }

    @PostMapping
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        String numero = req == null ? null : req.numeroConta();
        log.info("[LOGIN] POST /sessoes numeroConta={}", numero);

        if (req == null || req.numeroConta() == null || req.senha() == null) {
            log.warn("[LOGIN] -> 400: numeroConta/senha ausentes");
            return ResponseEntity.badRequest().body(new ErroResponse("numeroConta e senha são obrigatórios"));
        }

        ContaBancaria conta = banco.recuperarConta(req.numeroConta());

        // Resposta genérica (não revela se a conta existe) contra enumeração.
        if (conta == null || conta.getSenhaHash() == null
                || !encoder.matches(req.senha(), conta.getSenhaHash())) {
            log.warn("[LOGIN] -> 401 credenciais inválidas numeroConta={}", numero);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErroResponse("Credenciais inválidas"));
        }

        String token = sessoes.abrir(conta.getNumeroConta().getValor());
        log.info("[LOGIN] -> 200 OK numeroConta={} token={}...", numero, prefixo(token));
        return ResponseEntity.ok(new LoginResponse(token, conta.getNumeroConta().getValor()));
    }

    /** Só os primeiros caracteres do token vão para o log (o resto fica em segredo). */
    private static String prefixo(String token) {
        return token != null && token.length() >= 8 ? token.substring(0, 8) : token;
    }
}
