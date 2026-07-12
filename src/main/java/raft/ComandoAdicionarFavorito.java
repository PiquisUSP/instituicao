package raft;

import estruturas.conta.ContaBancaria;
import estruturas.conta.favorito.Favorito;
import estruturas.db.BancoDeDados;

public final class ComandoAdicionarFavorito implements Comando {

    private static final long serialVersionUID = 1L;

    private final String numeroContaDono;
    private final String id;
    private final String apelido;
    private final String chave;
    private final String idInstituicaoDestino;
    private final String numeroContaDestino;
    private final String nomeTitular;

    public ComandoAdicionarFavorito(String numeroContaDono, String id, String apelido, String chave,
                                    String idInstituicaoDestino, String numeroContaDestino, String nomeTitular) {
        this.numeroContaDono = numeroContaDono;
        this.id = id;
        this.apelido = apelido;
        this.chave = chave;
        this.idInstituicaoDestino = idInstituicaoDestino;
        this.numeroContaDestino = numeroContaDestino;
        this.nomeTitular = nomeTitular;
    }

    @Override
    public int aplicar(BancoDeDados db) {
        ContaBancaria conta = db.recuperarConta(numeroContaDono);
        if (conta == null) {
            return 404;
        }
        conta.adicionarFavorito(
                new Favorito(id, apelido, chave, idInstituicaoDestino, numeroContaDestino, nomeTitular));
        return 200;
    }

    @Override
    public String toString() {
        return "ComandoAdicionarFavorito{dono=" + numeroContaDono + ", apelido=" + apelido
                + ", destino=" + idInstituicaoDestino + "·" + numeroContaDestino + "}";
    }
}
