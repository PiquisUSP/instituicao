package estruturas.conta;

import estruturas.CPF;
import estruturas.conta.extrato.Extrato;
import estruturas.conta.favorito.Favorito;
import estruturas.financeiro.Saldo;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class ContaBancaria implements Serializable {
    private static final long serialVersionUID = 1L;

    private NumeroConta numeroConta;
    private CPF cpf;
    private String nome;
    private Saldo saldo;
    private Extrato extrato;
    private String senhaHash; // hash BCrypt (a senha nunca é guardada em claro)
    // Destinos salvos com apelido (estilo Inter). CopyOnWriteArrayList: a thread do
    // Raft escreve e o REST lê/serializa (snapshot) ao mesmo tempo, sem travar.
    private List<Favorito> favoritos = new CopyOnWriteArrayList<>();

    public ContaBancaria(String numeroConta, String cpf, String nome, String senhaHash) {
        this.numeroConta = new NumeroConta(numeroConta);
        this.cpf = new CPF(cpf);
        this.nome = nome;
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

    public String getNome() {
        return nome;
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

    // Cópia imutável para leitura (o REST monta a resposta a partir daqui).
    public List<Favorito> getFavoritos() {
        return List.copyOf(favoritosInternos());
    }

    public void adicionarFavorito(Favorito favorito) {
        favoritosInternos().add(favorito);
    }

    public boolean removerFavorito(String favoritoId) {
        return favoritosInternos().removeIf(f -> f.getId().equals(favoritoId));
    }

    // Snapshots antigos (desserializados) não têm a lista — reidrata sob demanda.
    private List<Favorito> favoritosInternos() {
        List<Favorito> atual = this.favoritos;
        if (atual == null) {
            atual = new CopyOnWriteArrayList<>();
            this.favoritos = atual;
        }
        return atual;
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
