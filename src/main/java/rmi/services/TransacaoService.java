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

        TransacaoPendente jaExiste = banco.recuperarPendente(idTransacao);
        if (jaExiste != null) {
            LOG.info("[PREPARE] {} já preparada, repetindo voto sim", idTransacao);
            return true;
        }

        boolean origemLocal = idInstituicao.equals(idInstituicaoOrigem);
        boolean destinoLocal = idInstituicao.equals(idInstituicaoDestino);
        if (!origemLocal && !destinoLocal) {
            LOG.warn("[PREPARE] {} não envolve esta instituição, voto não", idTransacao);
            return false;
        }

        TransacaoPendente pendente = new TransacaoPendente(idTransacao,
                idInstituicaoOrigem, contaOrigem, idInstituicaoDestino, contaDestino,
                valorCentavos, System.currentTimeMillis(), origemLocal, destinoLocal);

        int status = aplicador.registrar(new ComandoPreparar(pendente));
        boolean voto = status == 200;
        LOG.info("[PREPARE] {} voto={}", idTransacao, voto);
        return voto;
    }
    @Override
    public boolean commit(UUID idTransacao) throws RemoteException {
        TransacaoPendente transacaoPendente = banco.recuperarPendente(idTransacao);
        if (transacaoPendente == null) {
            LOG.info("[COMMIT] {} sem pendência, nada a fazer", idTransacao);
            return true;
        }

        ComandoComitar comando = new ComandoComitar(idTransacao);
        int status = aplicador.registrar(comando);
        boolean voto = status == 200;

        LOG.info("[COMMIT] {}", idTransacao);

        return voto;
    }

    @Override
    public boolean cancel(UUID idTransacao) throws RemoteException {
        if (banco.recuperarPendente(idTransacao) == null) {
            LOG.info("[CANCEL] {} sem pendência, nada a liberar", idTransacao);
            return true;
        }

        int status = aplicador.registrar(new ComandoCancelar(idTransacao));
        boolean ok = status == 200;
        LOG.info("[CANCEL] {} ok={}", idTransacao, ok);
        return ok;
    }
}
