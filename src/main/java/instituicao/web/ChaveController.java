package instituicao.web;

import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import estruturas.db.BancoDeDados;
import instituicao.chaves.ClienteServidorChaves;
import instituicao.chaves.ServidorChavesIndisponivel;
import instituicao.chaves.TipoChave;
import instituicao.seguranca.SessaoService;
import instituicao.web.dto.ChaveResponse;
import instituicao.web.dto.ErroResponse;
import instituicao.web.dto.RegistrarChaveRequest;

/**
 * Ponte com o {@code servidor-de-chaves}. Registra chaves apontando para
 * contas desta instituição e consulta existência de chaves.
 *
 * <p>Registrar uma chave exige estar logado na conta (Bearer token). A criação da
 * conta e o registro da chave são passos separados de propósito: o servidor de
 * chaves é externo e pode estar fora do ar, e isso não deve impedir criar contas.
 */
@RestController
public class ChaveController {

    private static final Logger log = LoggerFactory.getLogger(ChaveController.class);

    private final BancoDeDados banco;
    private final SessaoService sessoes;
    private final ClienteServidorChaves chaves;
    private final String idInstituicao;

    public ChaveController(BancoDeDados banco, SessaoService sessoes, ClienteServidorChaves chaves,
                           @Value("${instituicao.id:INST-0001}") String idInstituicao) {
        this.banco = banco;
        this.sessoes = sessoes;
        this.chaves = chaves;
        this.idInstituicao = idInstituicao;
    }

    /** Registra uma chave para a conta (exige login desta conta). */
    @PostMapping("/contas/{numero}/chaves")
    public ResponseEntity<?> registrar(@PathVariable String numero,
                                       @RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestBody RegistrarChaveRequest req) {
        log.info("[CHAVE] POST /contas/{}/chaves tipo={}", numero, req == null ? null : req.tipo());

        if (!sessoes.autorizadoHeader(authorization, numero)) {
            log.warn("[CHAVE] -> 401 acesso negado à conta {}", numero);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErroResponse("Não autenticado para esta conta"));
        }
        if (banco.recuperarConta(numero) == null) {
            log.warn("[CHAVE] -> 404 conta {} inexistente", numero);
            return ResponseEntity.notFound().build();
        }

        TipoChave tipo = parseTipo(req == null ? null : req.tipo());
        if (tipo == null) {
            log.warn("[CHAVE] -> 400 tipo inválido");
            return ResponseEntity.badRequest()
                    .body(new ErroResponse("tipo inválido (use CPF, TELEFONE, EMAIL ou ALEATORIA)"));
        }
        String valor = req.valor();
        if (tipo != TipoChave.ALEATORIA && (valor == null || valor.isBlank())) {
            log.warn("[CHAVE] -> 400 valor obrigatório para tipo {}", tipo);
            return ResponseEntity.badRequest().body(new ErroResponse("valor é obrigatório para tipo " + tipo));
        }

        try {
            log.info("[CHAVE] chamando servidor-de-chaves via RMI (tipo={}, numeroConta={}, idInstituicao={})...",
                    tipo, numero, idInstituicao);
            int status = chaves.registrar(tipo, idInstituicao, numero, valor);
            log.info("[CHAVE] servidor-de-chaves respondeu status={}", status);
            return switch (status) {
                case 200 -> {
                    log.info("[CHAVE] -> 201 chave registrada (conta {})", numero);
                    yield ResponseEntity.status(HttpStatus.CREATED)
                            .body(new ChaveResponse(tipo.name(), valor, numero));
                }
                case 403 -> {
                    log.warn("[CHAVE] -> 409 chave já registrada");
                    yield ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(new ErroResponse("Chave já registrada no servidor de chaves"));
                }
                default -> {
                    log.warn("[CHAVE] -> 502 servidor de chaves recusou (status {})", status);
                    yield ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(new ErroResponse("Servidor de chaves recusou o registro (status " + status + ")"));
                }
            };
        } catch (ServidorChavesIndisponivel e) {
            log.error("[CHAVE] -> 502 servidor de chaves indisponível: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ErroResponse("Servidor de chaves indisponível"));
        }
    }

    /** Consulta se uma chave existe no servidor de chaves (público). */
    @GetMapping("/chaves/{valor}/existe")
    public ResponseEntity<?> existe(@PathVariable String valor) {
        log.info("[CHAVE] GET /chaves/{}/existe", valor);
        try {
            boolean existe = chaves.existe(valor);
            log.info("[CHAVE] existe({}) = {}", valor, existe);
            return ResponseEntity.ok(Map.of("valor", valor, "existe", existe));
        } catch (ServidorChavesIndisponivel e) {
            log.error("[CHAVE] -> 502 servidor de chaves indisponível: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ErroResponse("Servidor de chaves indisponível"));
        }
    }

    private static TipoChave parseTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return null;
        }
        try {
            return TipoChave.valueOf(tipo.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
