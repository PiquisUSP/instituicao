package raft;

import estruturas.conta.ContaBancaria;
import estruturas.db.BancoDeDados;

// Remove um favorito da conta do dono, pelo id. 404 se a conta ou o favorito não existem.
public final class ComandoRemoverFavorito implements Comando {

    private static final long serialVersionUID = 1L;

    private final String numeroContaDono;
    private final String favoritoId;

    public ComandoRemoverFavorito(String numeroContaDono, String favoritoId) {
        this.numeroContaDono = numeroContaDono;
        this.favoritoId = favoritoId;
    }

    @Override
    public int aplicar(BancoDeDados db) {
        ContaBancaria conta = db.recuperarConta(numeroContaDono);
        if (conta == null) {
            return 404;
        }
        return conta.removerFavorito(favoritoId) ? 200 : 404;
    }

    @Override
    public String toString() {
        return "ComandoRemoverFavorito{dono=" + numeroContaDono + ", favoritoId=" + favoritoId + "}";
    }
}
