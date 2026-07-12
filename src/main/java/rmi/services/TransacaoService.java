package rmi.services;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import estruturas.db.BancoDeDados;
import estruturas.transacao.TransacaoPendente;
import raft.AplicadorDeContas;
import raft.ComandoPreparar;
import raft.ComandoComitar;
import raft.ComandoCancelar;
import rmi.TransacaoInterface;

// PREPARE do 2PC no lado participante. Descobre quais pontas da transferência são desta
// instituição (origem, destino, ou as duas se for interna), submete um ComandoPreparar ao
// Raft (reserva na origem, valida o destino) e devolve o voto ao Banco Central. Publicado
// no mesmo registry da descoberta de líder.
public class TransacaoService extends UnicastRemoteObject implements TransacaoInterface {

    private static final Logger LOG = LoggerFactory.getLogger(TransacaoService.class);

    private final AplicadorDeContas aplicador;
    private final BancoDeDados banco;
    private final String idInstituicao;

    public TransacaoService(AplicadorDeContas aplicador, BancoDeDados banco, String idInstituicao, int portaExport)
            throws RemoteException {
        super(portaExport);
        this.aplicador = aplicador;
        this.banco = banco;
        this.idInstituicao = idInstituicao;
    }

    @Override
    public boolean prepare(UUID idTransacao, String idInstituicaoOrigem, String contaOrigem,
            String idInstituicaoDestino, String contaDestino, long valorCentavos) throws RemoteException {

        // idempotência: se já votei nesta transação, repito o voto (sim). O BC pode
        // reenviar o PREPARE por reenvio de TCP ou recuperação.
        TransacaoPendente jaExiste = banco.recuperarPendente(idTransacao);
        if (jaExiste != null) {
            LOG.info("[PREPARE] {} já preparada aqui; repetindo voto sim", idTransacao);
            return true;
        }

        // quais pontas são desta instituição? numa transferência interna, as duas.
        boolean origemLocal = idInstituicao.equals(idInstituicaoOrigem);
        boolean destinoLocal = idInstituicao.equals(idInstituicaoDestino);
        if (!origemLocal && !destinoLocal) {
            LOG.warn("[PREPARE] {} não envolve esta instituição ({}); voto não", idTransacao, idInstituicao);
            return false;
        }

        // Timestamp gerado aqui (uma vez) e carregado na pendência: o comando Raft precisa ser
        // determinístico, então não pode ler o relógio dentro do aplicar().
        TransacaoPendente pendente = new TransacaoPendente(idTransacao,
                idInstituicaoOrigem, contaOrigem, idInstituicaoDestino, contaDestino,
                valorCentavos, System.currentTimeMillis(), origemLocal, destinoLocal);

        int status = aplicador.registrar(new ComandoPreparar(pendente));
        boolean voto = status == 200;
        LOG.info("[PREPARE] {} origemLocal={} destinoLocal={} valor={}c -> status={} voto={}",
                idTransacao, origemLocal, destinoLocal, valorCentavos, status, voto);
        return voto;
    }
    @Override
    public boolean commit(UUID idTransacao) throws RemoteException {
        TransacaoPendente transacaoPendente = banco.recuperarPendente(idTransacao);
        if (transacaoPendente == null) {
            LOG.info("[COMMIT] {} sem pendência aqui; já comitada ou nada a fazer (idempotente)", idTransacao);
            return true;
        }

        ComandoComitar comando = new ComandoComitar(idTransacao);
        int status = aplicador.registrar(comando);
        boolean voto = status == 200;
        
        LOG.info("[COMMIT] transação {}",
                idTransacao);

        return voto;
    }

    @Override
    public boolean cancel(UUID idTransacao) throws RemoteException {
        if (banco.recuperarPendente(idTransacao) == null) {
            LOG.info("[CANCEL] {} sem pendência aqui; nada a liberar (idempotente)", idTransacao);
            return true;
        }

        int status = aplicador.registrar(new ComandoCancelar(idTransacao));
        boolean ok = status == 200;
        LOG.info("[CANCEL] {} -> status={} ok={}", idTransacao, status, ok);
        return ok;
    }
}
