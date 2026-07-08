package instituicao.web.dto;

/** Saldo de uma conta, em centavos. */
public record SaldoResponse(String numeroConta, long saldoCentavos) {
}
