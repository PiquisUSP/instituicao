package instituicao.web.dto;

import java.util.List;

// Chaves atreladas a uma conta.
public record ChavesResponse(String numeroConta, List<String> chaves) {
}
