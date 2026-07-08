package raft;

import estruturas.db.BancoDeDados;
import estruturas.db.exceptions.conta.ContaJaRegistrada;

/**
 * Aplica os comandos direto num {@link BancoDeDados} local, sem consenso.
 *
 * <p>Útil para os testes e para rodar um único processo (modo
 * {@code bancocentral.raft.enabled=false}), sem precisar de maioria de nós.
 */
public class AplicadorLocal implements AplicadorDeContas {

    private final BancoDeDados db;

    public AplicadorLocal(BancoDeDados db) {
        this.db = db;
    }

    @Override
    public int registrar(ComandoCriarConta comando) {
        try {
            db.adicionarConta(comando.reconstruirConta());
            return 200;
        } catch (ContaJaRegistrada e) {
            return 403;
        } catch (Exception e) {
            return 500;
        }
    }
}
