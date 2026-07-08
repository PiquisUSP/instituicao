package instituicao.web;

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
        if (req == null || req.numeroConta() == null || req.senha() == null) {
            return ResponseEntity.badRequest().body(new ErroResponse("numeroConta e senha são obrigatórios"));
        }

        ContaBancaria conta = banco.recuperarConta(req.numeroConta());

        // Resposta genérica (não revela se a conta existe) contra enumeração.
        if (conta == null || conta.getSenhaHash() == null
                || !encoder.matches(req.senha(), conta.getSenhaHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErroResponse("Credenciais inválidas"));
        }

        String token = sessoes.abrir(conta.getNumeroConta().getValor());
        return ResponseEntity.ok(new LoginResponse(token, conta.getNumeroConta().getValor()));
    }
}
