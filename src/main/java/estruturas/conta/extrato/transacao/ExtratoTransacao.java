package estruturas.conta.extrato.transacao;

import estruturas.CPF;
import estruturas.conta.NumeroConta;
import estruturas.conta.ref.ContaBancariaRef;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class ExtratoTransacao implements Serializable {
    private UUID id;
    private ContaBancariaRef contaBancariaOrigem, contaBancariaDestino;
    private LocalDateTime horaTransacao;
    private long valorTransacaoCentavos = 0L;

    public ExtratoTransacao(UUID id, ContaBancariaRef contaBancariaOrigem, ContaBancariaRef contaBancariaDestino, LocalDateTime horaTransacao, long valorTransacaoCentavos) {
        this.id = id;
        this.contaBancariaOrigem = contaBancariaOrigem;
        this.contaBancariaDestino = contaBancariaDestino;
        this.horaTransacao = horaTransacao;
        this.valorTransacaoCentavos = valorTransacaoCentavos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtratoTransacao that = (ExtratoTransacao) o;
        return id == that.id && valorTransacaoCentavos == that.valorTransacaoCentavos && Objects.equals(contaBancariaOrigem, that.contaBancariaOrigem) && Objects.equals(contaBancariaDestino, that.contaBancariaDestino) && Objects.equals(horaTransacao, that.horaTransacao);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, contaBancariaOrigem, contaBancariaDestino, horaTransacao, valorTransacaoCentavos);
    }

    public UUID getId() {
        return id;
    }

    public long getValorTransacaoCentavos() {
        return valorTransacaoCentavos;
    }

    public NumeroConta getContaBancariaOrigem() {
        return contaBancariaOrigem.getNumeroConta();
    }

    public NumeroConta getContaBancariaDestino() {
        return contaBancariaDestino.getNumeroConta();
    }

    public LocalDateTime getHoraTransacao() {
        return horaTransacao;
    }
}
