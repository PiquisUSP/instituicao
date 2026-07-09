package instituicao.web.dto;

// Corpo do POST /contas/{numero}/favoritos. chave é opcional (favorito por chave);
// idInstituicao + numeroConta identificam o destino já resolvido; nome é o titular.
public record AdicionarFavoritoRequest(String apelido, String chave,
                                       String idInstituicao, String numeroConta, String nome) {
}
