package instituicao.web.dto;

import java.util.List;

public record ExtratoResponse(String numeroConta, List<TransacaoResponse> transacoes) {
}
