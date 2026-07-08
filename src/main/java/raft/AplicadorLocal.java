package raft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import estruturas.db.BancoDeDados;
import estruturas.db.exceptions.conta.ContaJaRegistrada;

/**
 * Aplica os comandos direto num {@link BancoDeDados} local, sem consenso.
 *
 * <p>Útil para os testes e para rodar um único processo (modo
 * {@code instituicao.raft.enabled=false}), sem precisar de maioria de nós.
 */
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
