package instituicao.web.dto;

/** Dados públicos de uma conta (nunca inclui senha nem saldo). */
public record ContaResponse(String numeroConta, String cpf) {
}
