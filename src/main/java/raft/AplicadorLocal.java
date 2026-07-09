package raft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import estruturas.db.BancoDeDados;

// Aplica direto no banco em memória, sem consenso. Serve para testes e para o modo
// de processo único (instituicao.raft.enabled=false).
public class AplicadorLocal implements AplicadorDeContas {

    private static final Logger LOG = LoggerFactory.getLogger(AplicadorLocal.class);

    private final BancoDeDados db;

    public AplicadorLocal(BancoDeDados db) {
        this.db = db;
    }

    @Override
    public int registrar(Comando comando) {
        LOG.info("[LOCAL] aplicando {} direto no banco (sem consenso)", comando);
        try {
            int status = comando.aplicar(db);
            LOG.info("[LOCAL] status={}", status);
            return status;
        } catch (Exception e) {
            LOG.error("[LOCAL] status=500 (falha inesperada)", e);
            return 500;
        }
    }
}
