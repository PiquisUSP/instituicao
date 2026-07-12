package raft;

import estruturas.conta.ContaBancaria;
import estruturas.db.BancoDeDados;
import estruturas.transacao.TransacaoPendente;

public final class ComandoPreparar implements Comando {

    private static final long serialVersionUID = 1L;

    private final TransacaoPendente pendente;

    public ComandoPreparar(TransacaoPendente pendente) {
        this.pendente = pendente;
    }

    @Override
    public int aplicar(BancoDeDados db) {
        if (db.recuperarPendente(pendente.getId()) != null) {
            return 200;
        }

        if (pendente.isOrigemLocal()) {
            ContaBancaria origem = db.recuperarConta(pendente.getContaOrigem());
            if (origem == null) {
                return 404;
            }
            if (!origem.getSaldo().reservar(pendente.getValorCentavos())) {
                return 403;
            }
        }

        if (pendente.isDestinoLocal() && db.recuperarConta(pendente.getContaDestino()) == null) {
            return 404;
        }

        db.registrarPendente(pendente);
        return 200;
    }

    @Override
    public String toString() {
        return "ComandoPreparar{" + pendente + "}";
    }
}
