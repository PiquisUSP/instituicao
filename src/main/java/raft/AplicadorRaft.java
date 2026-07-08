package raft;

import org.apache.ratis.client.RaftClient;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aplica os comandos submetendo-os ao grupo Raft via {@link RaftClient}.
 *
 * <p>O client descobre o líder sozinho (mesmo que este nó seja um seguidor),
 * envia o comando com {@code send()} — que só retorna depois de a entrada ser
 * replicada para a maioria e commitada — e devolve o status produzido pela
 * StateMachine do líder ao aplicar a transação.
 */
public class AplicadorRaft implements AplicadorDeContas {

    private static final Logger LOG = LoggerFactory.getLogger(AplicadorRaft.class);

    private final RaftClient client;

    public AplicadorRaft(RaftClient client) {
        this.client = client;
    }

    @Override
    public int registrar(ComandoCriarConta comando) {
        try {
            LOG.info("[RAFT] submetendo {} (send -> replica e aguarda commit da maioria)...", comando);
            // send() = escrita: vira entrada no log, é replicada e commitada
            // pela maioria antes de retornar.
            RaftClientReply reply = client.io().send(Message.valueOf(comando.serializar()));

            if (reply.isSuccess()) {
                String resposta = reply.getMessage().getContent().toStringUtf8().trim();
                LOG.info("[RAFT] commit confirmado pela maioria; status da StateMachine={}", resposta);
                return Integer.parseInt(resposta);
            }

            LOG.warn("[RAFT] Raft não confirmou o comando {}: {}", comando, reply);
            return 500;
        } catch (Exception e) {
            LOG.error("[RAFT] Falha ao replicar comando {} via Raft", comando, e);
            return 500;
        }
    }
}
