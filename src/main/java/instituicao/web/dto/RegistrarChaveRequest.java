package instituicao.web.dto;

/**
 * Corpo do POST /contas/{numero}/chaves.
 *
 * @param tipo  CPF | TELEFONE | EMAIL | ALEATORIA.
 * @param valor valor da chave (obrigatório, exceto para ALEATORIA).
 */
public record RegistrarChaveRequest(String tipo, String valor) {
}
