package instituicao.web.dto;

/** Resposta do login: token de sessão a ser enviado em Authorization: Bearer. */
public record LoginResponse(String token, String numeroConta) {
}
