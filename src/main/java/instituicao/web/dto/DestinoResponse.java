package instituicao.web.dto;

// Destino de transferência já resolvido (instituição, conta e titular).
public record DestinoResponse(String idInstituicao, String numeroConta, String nome) {
}
