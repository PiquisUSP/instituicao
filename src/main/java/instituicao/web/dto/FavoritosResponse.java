package instituicao.web.dto;

import java.util.List;

// Favoritos de transferência atrelados a uma conta.
public record FavoritosResponse(String numeroConta, List<FavoritoResponse> favoritos) {
}
