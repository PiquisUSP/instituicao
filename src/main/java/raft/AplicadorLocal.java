package raft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import estruturas.db.BancoDeDados;

public class AplicadorLocal implements AplicadorDeContas {

    private static final Logger LOG = LoggerFactory.getLogger(AplicadorLocal.class);

    private final BancoDeDados db;

    public AplicadorLocal(BancoDeDados db) {
        this.db = db;
    }

    @Override
    public int registrar(Comando comando) {
        LOG.info("[LOCAL] aplicando {}", comando);
        try {
            int status = comando.aplicar(db);
            return status;
        } catch (Exception e) {
            LOG.error("[LOCAL] falha ao aplicar", e);
            return 500;
        }
    }
}
