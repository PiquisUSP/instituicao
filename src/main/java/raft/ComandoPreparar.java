package raft;

import estruturas.conta.ContaBancaria;
import estruturas.db.BancoDeDados;
import estruturas.transacao.TransacaoPendente;

// PREPARE do 2PC, replicado via Raft. Carrega a pendência já pronta (montada no handler) e,
// se a conta de origem é desta instituição, reserva o valor (403 se faltar saldo). O destino
// só é validado. Guarda a pendência para o COMMIT/ABORT achá-la. Idempotente: se já existe
// (replay do log ou PREPARE repetido), não reserva de novo.
public final class ComandoPreparar implements Comando {

    private static final long serialVersionUID = 1L;

    private final TransacaoPendente pendente;

    public ComandoPreparar(TransacaoPendente pendente) {
        this.pendente = pendente;
    }

    @Override
    public int aplicar(BancoDeDados db) {
        if (db.recuperarPendente(pendente.getId()) != null) {
            return 200; // já preparada — não repete a reserva
        }

        // reserva na origem (só quem envia prende saldo)
        if (pendente.isOrigemLocal()) {
            ContaBancaria origem = db.recuperarConta(pendente.getContaOrigem());
            if (origem == null) {
                return 404;
            }
            if (!origem.getSaldo().reservar(pendente.getValorCentavos())) {
                return 403; // sem saldo -> voto não
            }
        }

        // destino só confirma que a conta existe
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
