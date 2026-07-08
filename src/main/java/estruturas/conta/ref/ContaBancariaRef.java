package estruturas.conta.ref;

import estruturas.CPF;
import estruturas.conta.NumeroConta;
import estruturas.financeiro.Saldo;
import estruturas.instituicao.IdentificadorInstituicao;

import java.io.Serializable;
import java.util.Objects;

public class ContaBancariaRef implements Serializable {
    private IdentificadorInstituicao identificadorInstituicao;
    private NumeroConta numeroConta;

    public ContaBancariaRef(String identificadorInstituicao, String numeroConta) {
        this.identificadorInstituicao = new IdentificadorInstituicao(identificadorInstituicao);
        this.numeroConta = new NumeroConta(numeroConta);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContaBancariaRef that = (ContaBancariaRef) o;
        return Objects.equals(numeroConta, that.numeroConta) && Objects.equals(this.identificadorInstituicao, that.identificadorInstituicao);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identificadorInstituicao, numeroConta);
    }

    public NumeroConta getNumeroConta() {
        return numeroConta;
    }
}
