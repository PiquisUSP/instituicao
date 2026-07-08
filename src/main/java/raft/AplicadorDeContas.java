package raft;

/**
 * Abstrai <i>como</i> uma escrita (criação de conta) é efetivada.
 *
 * <p>Existem duas implementações:
 * <ul>
 *   <li>{@link AplicadorLocal} — aplica direto num {@code BancoDeDados} em memória,
 *       sem replicação. Usado em testes e no modo de processo único.</li>
 *   <li>{@link AplicadorRaft} — submete o comando ao grupo Raft via {@code RaftClient};
 *       o líder replica para a maioria e a StateMachine de cada nó aplica.</li>
 * </ul>
 *
 * <p>Assim o controller REST não sabe se está rodando isolado ou em cluster.
 */
public interface AplicadorDeContas {

    /**
     * Cria a conta descrita pelo comando.
     *
     * @return 200 se criou, 403 se a conta já existia, 500 em falha inesperada.
     */
    int registrar(ComandoCriarConta comando);
}
