package instituicao.web.dto;

/** Chave registrada no servidor de chaves para uma conta. */
public record ChaveResponse(String tipo, String valor, String numeroConta) {
}
