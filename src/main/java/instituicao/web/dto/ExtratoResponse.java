package instituicao.web.dto;

import java.util.List;

/** Extrato de uma conta: lista de transações (vazia enquanto não houver movimento). */
public record ExtratoResponse(String numeroConta, List<TransacaoResponse> transacoes) {
}
