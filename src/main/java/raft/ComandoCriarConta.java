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

/**
 * Comando de escrita que é gravado no log do Raft e replicado para todos os nós.
 *
 * <p>Guarda apenas dados primitivos (strings), nunca um objeto {@code ContaBancaria}
 * pronto — assim a serialização é estável e a reconstrução em cada nó é determinística.
 *
 * <p><b>Determinismo:</b> o número da conta já vem resolvido aqui (gerado no
 * controller <i>antes</i> do comando entrar no log). A StateMachine nunca gera
 * valores novos — apenas reaplica o que está registrado —, garantindo que todos os
 * nós cheguem ao mesmo estado.
 */
public final class ComandoCriarConta implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String numeroConta;
    private final String cpf;
    // Hash BCrypt da senha, já resolvido no controller antes de entrar no log.
    private final String senhaHash;

    public ComandoCriarConta(String numeroConta, String cpf, String senhaHash) {
        this.numeroConta = numeroConta;
        this.cpf = cpf;
        this.senhaHash = senhaHash;
    }

    /** Reconstrói a conta (revalida o CPF no construtor de {@code ContaBancaria}). */
    public ContaBancaria reconstruirConta() {
        return new ContaBancaria(numeroConta, cpf, senhaHash);
    }

    public String getNumeroConta() {
        return numeroConta;
    }

    public String getCpf() {
        return cpf;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    /** Serializa em uma String (Base64) para viajar como conteúdo de uma {@code Message} do Ratis. */
    public String serializar() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(this);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao serializar ComandoCriarConta", e);
        }
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    /** Reconstrói o comando a partir da String produzida por {@link #serializar()}. */
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
        // A senha (hash) fica de fora do log de texto por higiene.
        return "ComandoCriarConta{numeroConta=" + numeroConta + ", cpf=" + cpf + "}";
    }
}
