package instituicao.web.dto;

// Corpo do POST /contas. numeroConta é opcional (se faltar, o servidor gera um).
public record CriarContaRequest(String cpf, String nome, String senha, String numeroConta) {
}
