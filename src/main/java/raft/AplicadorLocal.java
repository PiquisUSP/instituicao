package raft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import estruturas.db.BancoDeDados;
import estruturas.db.exceptions.conta.ContaJaRegistrada;

// Aplica direto no banco em memória, sem consenso. Serve para testes e para o modo
// de processo único (instituicao.raft.enabled=false).
public class AplicadorLocal implements AplicadorDeContas {

    private static final Logger LOG = LoggerFactory.getLogger(AplicadorLocal.class);

    private final BancoDeDados db;

    public AplicadorLocal(BancoDeDados db) {
        this.db = db;
    }

    @Override
    public int registrar(ComandoCriarConta comando) {
        LOG.info("[LOCAL] aplicando {} direto no banco (sem consenso)", comando);
        try {
            db.adicionarConta(comando.reconstruirConta());
            LOG.info("[LOCAL] status=200 (conta criada)");
            return 200;
        } catch (ContaJaRegistrada e) {
            LOG.warn("[LOCAL] status=403 (conta já registrada)");
            return 403;
        } catch (Exception e) {
            LOG.error("[LOCAL] status=500 (falha inesperada)", e);
            return 500;
        }
    }
}
