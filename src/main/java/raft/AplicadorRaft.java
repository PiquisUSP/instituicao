package raft;

import org.apache.ratis.client.RaftClient;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AplicadorRaft implements AplicadorDeContas {

    private static final Logger LOG = LoggerFactory.getLogger(AplicadorRaft.class);

    private final RaftClient client;

    public AplicadorRaft(RaftClient client) {
        this.client = client;
    }

    @Override
    public int registrar(Comando comando) {
        try {
            LOG.info("[RAFT] submetendo {}", comando);
            RaftClientReply reply = client.io().send(Message.valueOf(Comandos.serializar(comando)));

            if (reply.isSuccess()) {
                String resposta = reply.getMessage().getContent().toStringUtf8().trim();
                LOG.info("[RAFT] commit confirmado, status={}", resposta);
                return Integer.parseInt(resposta);
            }

            LOG.warn("[RAFT] não confirmou {}: {}", comando, reply);
            return 500;
        } catch (Exception e) {
            LOG.error("[RAFT] falha ao replicar {}", comando, e);
            return 500;
        }
    }
}
