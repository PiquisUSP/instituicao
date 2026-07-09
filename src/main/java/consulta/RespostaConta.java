package consulta;

import java.io.Serializable;

// Resposta da consulta de uma conta (existe? nome do titular?). Contrato RMI
// compartilhado — precisa ser a mesma classe em todos os lados.
public class RespostaConta implements Serializable {

    private static final long serialVersionUID = 1L;

    public final boolean existe;
    public final String nome;

    public RespostaConta(boolean existe, String nome) {
        this.existe = existe;
        this.nome = nome;
    }

    public static RespostaConta naoExiste() {
        return new RespostaConta(false, null);
    }

    public static RespostaConta de(String nome) {
        return new RespostaConta(true, nome);
    }
}
