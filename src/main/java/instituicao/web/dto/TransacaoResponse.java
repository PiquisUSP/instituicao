package instituicao.web.dto;

/** Uma linha do extrato. */
public record TransacaoResponse(
        String id,
        String contaOrigem,
        String contaDestino,
        long valorCentavos,
        String horaTransacao) {
}
