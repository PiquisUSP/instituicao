package raft;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import estruturas.conta.ContaBancaria;
import estruturas.conta.extrato.transacao.ExtratoTransacao;
import estruturas.conta.ref.ContaBancariaRef;
import estruturas.db.BancoDeDados;
import estruturas.transacao.TransacaoPendente;

// COMMIT do 2PC, replicado via Raft. Efetiva o débito na origem e o crédito no destino (as
// pontas que são desta instituição), lança o extrato nas contas locais e remove a pendência.
// Idempotente: sem pendência (já comitada ou reenvio na recuperação), é no-op com sucesso.
public final class ComandoComitar implements Comando {

    private static final long serialVersionUID = 1L;

    private final java.util.UUID idTransacao;

    public ComandoComitar(java.util.UUID idTransacao) {
        this.idTransacao = idTransacao;
    }

    @Override
    public int aplicar(BancoDeDados db) {
        TransacaoPendente p = db.recuperarPendente(idTransacao);
        if (p == null) {
            return 200; // já comitada ou nada a fazer aqui — idempotente
        }

        // Timestamp veio da fase de PREPARE (determinístico) — usa fuso fixo (UTC) para o
        // LocalDateTime ficar igual em todas as réplicas.
        LocalDateTime hora = LocalDateTime.ofInstant(Instant.ofEpochMilli(p.getHoraEpochMillis()), ZoneOffset.UTC);
        ExtratoTransacao lancamento = new ExtratoTransacao(
                p.getId(),
                new ContaBancariaRef(p.getIdInstOrigem(), p.getContaOrigem()),
                new ContaBancariaRef(p.getIdInstDestino(), p.getContaDestino()),
                hora,
                p.getValorCentavos());

        if (p.isOrigemLocal()) {
            ContaBancaria origem = db.recuperarConta(p.getContaOrigem());
            if (origem != null) {
                origem.getSaldo().efetivarDebito(p.getValorCentavos());
                origem.getExtrato().adicionarTransacao(lancamento);
            }
        }
        if (p.isDestinoLocal()) {
            ContaBancaria destino = db.recuperarConta(p.getContaDestino());
            if (destino != null) {
                destino.getSaldo().creditar(p.getValorCentavos());
                destino.getExtrato().adicionarTransacao(lancamento);
            }
        }

        db.removerTransacaoPendente(p);
        return 200;
    }

    @Override
    public String toString() {
        return "ComandoComitar{idTransacao=" + idTransacao + "}";
    }
}
