package raft;

import org.apache.ratis.client.RaftClient;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Submete o comando ao grupo Raft. O client acha o líder sozinho; o send() só volta
// depois de replicado e commitado pela maioria, com o status vindo da StateMachine.
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
