package raft;

import java.util.UUID;

import estruturas.conta.ContaBancaria;
import estruturas.db.BancoDeDados;
import estruturas.transacao.TransacaoPendente;

// ABORT do 2PC, replicado via Raft. Libera a reserva feita na origem (o destino não reservou
// nada, só validou) e remove a pendência. Idempotente: sem pendência (votou não, já cancelada
// ou reenvio na recuperação), é no-op com sucesso.
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
            return 200; // nada a liberar — idempotente
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
