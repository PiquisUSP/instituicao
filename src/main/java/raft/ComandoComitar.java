package raft;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import estruturas.conta.ContaBancaria;
import estruturas.conta.extrato.transacao.ExtratoTransacao;
import estruturas.conta.ref.ContaBancariaRef;
import estruturas.db.BancoDeDados;
import estruturas.transacao.TransacaoPendente;

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
            return 200;
        }

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
