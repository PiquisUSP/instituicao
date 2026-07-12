package raft;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.util.Base64;

public final class Comandos {

    private Comandos() {
    }

    public static String serializar(Comando comando) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(comando);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao serializar comando", e);
        }
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    public static Comando desserializar(String dados) {
        byte[] bytes = Base64.getDecoder().decode(dados);
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (Comando) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException("Falha ao desserializar comando", e);
        }
    }
}
