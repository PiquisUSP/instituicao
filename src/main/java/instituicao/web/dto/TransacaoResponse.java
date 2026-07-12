package instituicao.web.dto;

public record TransacaoResponse(
        String id,
        String contaOrigem,
        String contaDestino,
        long valorCentavos,
        String horaTransacao) {
}
