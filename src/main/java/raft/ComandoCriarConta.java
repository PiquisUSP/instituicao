package raft;

import estruturas.conta.ContaBancaria;
import estruturas.db.BancoDeDados;
import estruturas.db.exceptions.conta.ContaJaRegistrada;

public final class ComandoCriarConta implements Comando {

    private static final long serialVersionUID = 1L;

    private final String numeroConta;
    private final String cpf;
    private final String nome;
    private final String senhaHash;
    private final long saldoInicialCentavos;

    public ComandoCriarConta(String numeroConta, String cpf, String nome, String senhaHash) {
        this(numeroConta, cpf, nome, senhaHash, 0L);
    }

    public ComandoCriarConta(String numeroConta, String cpf, String nome, String senhaHash, long saldoInicialCentavos) {
        this.numeroConta = numeroConta;
        this.cpf = cpf;
        this.nome = nome;
        this.senhaHash = senhaHash;
        this.saldoInicialCentavos = saldoInicialCentavos;
    }

    @Override
    public int aplicar(BancoDeDados db) {
        try {
            db.adicionarConta(reconstruirConta());
            return 200;
        } catch (ContaJaRegistrada e) {
            return 403;
        }
    }

    public ContaBancaria reconstruirConta() {
        return new ContaBancaria(numeroConta, cpf, nome, senhaHash, saldoInicialCentavos);
    }

    public String getNumeroConta() {
        return numeroConta;
    }

    public String getCpf() {
        return cpf;
    }

    public String getNome() {
        return nome;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    @Override
    public String toString() {
        return "ComandoCriarConta{numeroConta=" + numeroConta + ", cpf=" + cpf + "}";
    }
}
