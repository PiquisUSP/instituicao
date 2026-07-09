package instituicao.web;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import estruturas.conta.favorito.Favorito;
import estruturas.db.BancoDeDados;
import instituicao.seguranca.SessaoService;
import instituicao.web.dto.AdicionarFavoritoRequest;
import instituicao.web.dto.ErroResponse;
import instituicao.web.dto.FavoritoResponse;
import instituicao.web.dto.FavoritosResponse;
import raft.AplicadorDeContas;
import raft.ComandoAdicionarFavorito;
import raft.ComandoRemoverFavorito;

// Favoritos de transferência da conta (destinos salvos com apelido, estilo Inter).
// Ficam no banco replicado da instituição: gravar passa pelo Raft (aplicador), ler é
// local. Tudo exige login na conta dona.
@RestController
@RequestMapping("/contas/{numero}/favoritos")
public class FavoritoController {

    private static final Logger log = LoggerFactory.getLogger(FavoritoController.class);

    private final AplicadorDeContas aplicador;
    private final BancoDeDados banco;
    private final SessaoService sessoes;

    public FavoritoController(AplicadorDeContas aplicador, BancoDeDados banco, SessaoService sessoes) {
        this.aplicador = aplicador;
        this.banco = banco;
        this.sessoes = sessoes;
    }

    @GetMapping
    public ResponseEntity<?> listar(@PathVariable String numero,
                                    @RequestHeader(value = "Authorization", required = false) String authorization) {
        log.info("[FAV] GET /contas/{}/favoritos", numero);
        ResponseEntity<?> negado = exigir(numero, authorization);
        if (negado != null) {
            return negado;
        }
        List<FavoritoResponse> lista = banco.recuperarConta(numero).getFavoritos().stream()
                .map(FavoritoController::toResponse)
                .toList();
        log.info("[FAV] conta {} tem {} favorito(s)", numero, lista.size());
        return ResponseEntity.ok(new FavoritosResponse(numero, lista));
    }

    @PostMapping
    public ResponseEntity<?> adicionar(@PathVariable String numero,
                                       @RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestBody AdicionarFavoritoRequest req) {
        log.info("[FAV] POST /contas/{}/favoritos apelido={}", numero, req == null ? null : req.apelido());
        ResponseEntity<?> negado = exigir(numero, authorization);
        if (negado != null) {
            return negado;
        }

        if (req == null || req.apelido() == null || req.apelido().isBlank()) {
            log.warn("[FAV] -> 400 apelido ausente");
            return ResponseEntity.badRequest().body(new ErroResponse("apelido é obrigatório"));
        }
        boolean temDestino = req.idInstituicao() != null && !req.idInstituicao().isBlank()
                && req.numeroConta() != null && !req.numeroConta().isBlank();
        if (!temDestino) {
            log.warn("[FAV] -> 400 destino ausente");
            return ResponseEntity.badRequest()
                    .body(new ErroResponse("informe a instituição e a conta de destino"));
        }

        // id resolvido aqui (antes do Raft) para todos os nós gravarem o mesmo.
        String id = UUID.randomUUID().toString();
        String apelido = req.apelido().trim();
        String chave = req.chave() != null && !req.chave().isBlank() ? req.chave().trim() : null;
        String idInstituicao = req.idInstituicao().trim();
        String numeroConta = req.numeroConta().trim();
        String nome = req.nome() != null && !req.nome().isBlank() ? req.nome().trim() : numeroConta;

        int status = aplicador.registrar(new ComandoAdicionarFavorito(
                numero, id, apelido, chave, idInstituicao, numeroConta, nome));

        return switch (status) {
            case 200 -> {
                log.info("[FAV] -> 201 favorito salvo (conta {}, apelido={})", numero, apelido);
                yield ResponseEntity.status(HttpStatus.CREATED)
                        .body(new FavoritoResponse(id, apelido, chave, idInstituicao, numeroConta, nome));
            }
            case 404 -> ResponseEntity.notFound().build();
            default -> {
                log.error("[FAV] -> 503 falha ao replicar (status interno={})", status);
                yield ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new ErroResponse("Não foi possível salvar o favorito (há maioria de nós no ar?)"));
            }
        };
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> remover(@PathVariable String numero, @PathVariable String id,
                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
        log.info("[FAV] DELETE /contas/{}/favoritos/{}", numero, id);
        ResponseEntity<?> negado = exigir(numero, authorization);
        if (negado != null) {
            return negado;
        }

        int status = aplicador.registrar(new ComandoRemoverFavorito(numero, id));
        return switch (status) {
            case 200 -> {
                log.info("[FAV] -> 204 favorito removido (conta {})", numero);
                yield ResponseEntity.noContent().build();
            }
            case 404 -> ResponseEntity.notFound().build();
            default -> {
                log.error("[FAV] -> 503 falha ao replicar (status interno={})", status);
                yield ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new ErroResponse("Não foi possível remover o favorito (há maioria de nós no ar?)"));
            }
        };
    }

    // 401 se o token não autentica a conta, 404 se a conta não existe; null se ok.
    private ResponseEntity<?> exigir(String numero, String authorization) {
        if (!sessoes.autorizadoHeader(authorization, numero)) {
            log.warn("[FAV] -> 401 acesso negado à conta {}", numero);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErroResponse("Não autenticado para esta conta"));
        }
        if (banco.recuperarConta(numero) == null) {
            log.warn("[FAV] -> 404 conta {} inexistente", numero);
            return ResponseEntity.notFound().build();
        }
        return null;
    }

    private static FavoritoResponse toResponse(Favorito f) {
        return new FavoritoResponse(f.getId(), f.getApelido(), f.getChave(),
                f.getIdInstituicao(), f.getNumeroConta(), f.getNomeTitular());
    }
}
