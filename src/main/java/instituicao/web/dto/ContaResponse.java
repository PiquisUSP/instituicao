package instituicao.web.dto;

// Dados públicos de uma conta (sem senha nem saldo).
public record ContaResponse(String numeroConta, String cpf, String nome) {
}
