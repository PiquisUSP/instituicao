package raft;

import java.util.UUID;

import estruturas.conta.ContaBancaria;
import estruturas.db.BancoDeDados;
import estruturas.transacao.TransacaoPendente;

public final class ComandoCancelar implements Comando {

    private static final long serialVersionUID = 1L;

    private final UUID idTransacao;

    public ComandoCancelar(UUID idTransacao) {
        this.idTransacao = idTransacao;
    }

    @Override
    public int aplicar(BancoDeDados db) {
        TransacaoPendente p = db.recuperarPendente(idTransacao);
        if (p == null) {
            return 200;
        }

        if (p.isOrigemLocal()) {
            ContaBancaria origem = db.recuperarConta(p.getContaOrigem());
            if (origem != null) {
                origem.getSaldo().liberarReserva(p.getValorCentavos());
            }
        }

        db.removerTransacaoPendente(p);
        return 200;
    }

    @Override
    public String toString() {
        return "ComandoCancelar{idTransacao=" + idTransacao + "}";
    }
}
