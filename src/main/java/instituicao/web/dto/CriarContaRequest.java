package instituicao.web.dto;

/**
 * Corpo do POST /contas.
 *
 * @param cpf         CPF do titular (obrigatório; validado).
 * @param senha       senha de acesso (obrigatória; guardada como hash BCrypt).
 * @param numeroConta número da conta (opcional; se ausente, o servidor gera um).
 */
public record CriarContaRequest(String cpf, String senha, String numeroConta) {
}
