package instituicao.web.dto;

// Um favorito de transferência (destino salvo com apelido).
public record FavoritoResponse(String id, String apelido, String chave,
                               String idInstituicao, String numeroConta, String nome) {
}
