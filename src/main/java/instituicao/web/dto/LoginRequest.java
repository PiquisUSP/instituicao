package instituicao.web.dto;

/** Corpo do POST /sessoes (login). */
public record LoginRequest(String numeroConta, String senha) {
}
