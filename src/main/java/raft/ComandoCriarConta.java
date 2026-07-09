package raft;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.Base64;

import estruturas.conta.ContaBancaria;

// Comando que vai para o log do Raft e é replicado. Guarda só strings (número, cpf,
// hash da senha) — nunca a ContaBancaria pronta — para a reconstrução ser igual em
// todos os nós. O número e o hash já vêm resolvidos do controller (determinismo).
public final class ComandoCriarConta implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String numeroConta;
    private final String cpf;
    private final String nome;
    private final String senhaHash;

    public ComandoCriarConta(String numeroConta, String cpf, String nome, String senhaHash) {
        this.numeroConta = numeroConta;
        this.cpf = cpf;
        this.nome = nome;
        this.senhaHash = senhaHash;
    }

    public ContaBancaria reconstruirConta() {
        return new ContaBancaria(numeroConta, cpf, nome, senhaHash);
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

    // Base64 para viajar como conteúdo de uma Message do Ratis.
    public String serializar() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(this);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao serializar ComandoCriarConta", e);
        }
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    public static ComandoCriarConta desserializar(String dados) {
        byte[] bytes = Base64.getDecoder().decode(dados);
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ComandoCriarConta) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException("Falha ao desserializar ComandoCriarConta", e);
        }
    }

    @Override
    public String toString() {
        // não loga o hash da senha
        return "ComandoCriarConta{numeroConta=" + numeroConta + ", cpf=" + cpf + "}";
    }
}
