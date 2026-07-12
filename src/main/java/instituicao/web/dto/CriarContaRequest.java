package instituicao.web.dto;

public record CriarContaRequest(String cpf, String nome, String senha, String numeroConta) {
}
