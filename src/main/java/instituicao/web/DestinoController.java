package instituicao.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import consulta.RespostaConta;
import instituicao.bancocentral.BancoCentralIndisponivel;
import instituicao.bancocentral.ClienteBancoCentral;
import instituicao.chaves.ClienteServidorChaves;
import instituicao.chaves.ServidorChavesIndisponivel;
import instituicao.web.dto.DestinoResponse;
import instituicao.web.dto.ErroResponse;

@RestController
public class DestinoController {

    private static final Logger log = LoggerFactory.getLogger(DestinoController.class);

    private final ClienteServidorChaves chaves;
    private final ClienteBancoCentral bancoCentral;

    public DestinoController(ClienteServidorChaves chaves, ClienteBancoCentral bancoCentral) {
        this.chaves = chaves;
        this.bancoCentral = bancoCentral;
    }

    @GetMapping("/destino")
    public ResponseEntity<?> resolver(@RequestParam(required = false) String chave,
                                      @RequestParam(required = false) String instituicao,
                                      @RequestParam(required = false) String conta) {
        String idInstituicao;
        String numeroConta;

        if (chave != null && !chave.isBlank()) {
            try {
                String[] r = chaves.resolverChave(chave.trim());
                if (r == null) {
                    log.warn("[DESTINO] chave não encontrada");
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ErroResponse("Chave não encontrada"));
                }
                idInstituicao = r[0];
                numeroConta = r[1];
            } catch (ServidorChavesIndisponivel e) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(new ErroResponse("Servidor de chaves indisponível"));
            }
        } else if (instituicao != null && !instituicao.isBlank() && conta != null && !conta.isBlank()) {
            idInstituicao = instituicao.trim();
            numeroConta = conta.trim();
        } else {
            return ResponseEntity.badRequest()
                    .body(new ErroResponse("Informe uma chave, ou instituição e conta"));
        }

        try {
            RespostaConta resp = bancoCentral.consultarConta(idInstituicao, numeroConta);
            if (!resp.existe) {
                log.warn("[DESTINO] conta {}/{} não encontrada", idInstituicao, numeroConta);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErroResponse("Conta de destino não encontrada"));
            }
            log.info("[DESTINO] resolvido {}/{} ({})", idInstituicao, numeroConta, resp.nome);
            return ResponseEntity.ok(new DestinoResponse(idInstituicao, numeroConta, resp.nome));
        } catch (BancoCentralIndisponivel e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ErroResponse("Banco Central indisponível"));
        }
    }
}
