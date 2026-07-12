package instituicao.web.dto;

public record TransferirRequest(String idInstituicaoDestino, String contaDestino, long valorCentavos) {
}
