package estruturas.conta;

import estruturas.CPF;
import estruturas.conta.extrato.Extrato;
import estruturas.financeiro.Saldo;

import java.io.Serializable;
import java.util.Objects;

public class ContaBancaria implements Serializable {
    private static final long serialVersionUID = 1L;

    private NumeroConta numeroConta;
    private CPF cpf;
    private Saldo saldo;
    private Extrato extrato;
    private String senhaHash; // hash BCrypt (a senha nunca é guardada em claro)

    public ContaBancaria(String numeroConta, String cpf, String senhaHash) {
        this.numeroConta = new NumeroConta(numeroConta);
        this.cpf = new CPF(cpf);
        this.saldo = new Saldo(0L);
        this.extrato = new Extrato();
        this.senhaHash = senhaHash;
    }

    public NumeroConta getNumeroConta() {
        return numeroConta;
    }

    public CPF getCpf() {
        return cpf;
    }

    public Saldo getSaldo() {
        return saldo;
    }

    public Extrato getExtrato() {
        return extrato;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContaBancaria that = (ContaBancaria) o;
        return Objects.equals(cpf, that.cpf);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(cpf);
    }
}
