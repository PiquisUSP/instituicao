package raft;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.ratis.RaftConfigKeys;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.rpc.SupportedRpcType;
import org.apache.ratis.server.RaftServerConfigKeys;

// Config estática do cluster da instituição: porta Raft (gRPC) de cada nó e o id do
// grupo (igual em todos). A porta HTTP fica com o Spring (server.port, por profile).
// Portas Raft 7001-7003 (diferentes das 6001-6003 do servidor-de-chaves, para os
// dois clusters coexistirem na mesma máquina). Host: 127.0.0.1 local; em Docker, via
// RAFT_HOST_N1/N2/N3.
public final class ClusterConfig {

    private ClusterConfig() {
    }

    // Id do grupo Raft — precisa ser idêntico em todos os nós.
    public static final RaftGroupId GROUP_ID =
            RaftGroupId.valueOf(UUID.fromString("9c1e2d3a-5b6f-4a7c-8d9e-0f1a2b3c4d5e"));

    private static final Map<String, Integer> PORTA_RAFT = new LinkedHashMap<>();

    static {
        PORTA_RAFT.put("n1", 7001);
        PORTA_RAFT.put("n2", 7002);
        PORTA_RAFT.put("n3", 7003);
    }

    public static boolean noValido(String id) {
        return PORTA_RAFT.containsKey(id);
    }

    public static List<String> ids() {
        return new ArrayList<>(PORTA_RAFT.keySet());
    }

    public static int portaRaft(String id) {
        String env = System.getenv("RAFT_PORT");
        return (env != null && !env.isBlank()) ? Integer.parseInt(env.trim()) : PORTA_RAFT.get(id);
    }

    public static File diretorioStorage(String id) {
        return new File("raft-storage", id);
    }

    private static String host(String id) {
        String env = System.getenv("RAFT_HOST_" + id.toUpperCase());
        return (env != null && !env.isBlank()) ? env.trim() : "127.0.0.1";
    }

    private static String enderecoRaft(String id) {
        return host(id) + ":" + portaRaft(id);
    }

    public static RaftGroup grupo() {
        List<RaftPeer> peers = new ArrayList<>();
        for (String id : PORTA_RAFT.keySet()) {
            peers.add(RaftPeer.newBuilder()
                    .setId(id)
                    .setAddress(enderecoRaft(id))
                    .build());
        }
        return RaftGroup.valueOf(GROUP_ID, peers);
    }

    public static RaftProperties propriedadesServidor(String id) {
        RaftProperties props = new RaftProperties();

        RaftConfigKeys.Rpc.setType(props, SupportedRpcType.GRPC);
        GrpcConfigKeys.Server.setPort(props, portaRaft(id));

        RaftServerConfigKeys.setStorageDir(props, Collections.singletonList(diretorioStorage(id)));

        // Snapshot automático a cada 10 entradas (persiste o estado e compacta o log).
        RaftServerConfigKeys.Snapshot.setAutoTriggerEnabled(props, true);
        RaftServerConfigKeys.Snapshot.setAutoTriggerThreshold(props, 10L);

        return props;
    }

    public static RaftProperties propriedadesCliente() {
        RaftProperties props = new RaftProperties();
        RaftConfigKeys.Rpc.setType(props, SupportedRpcType.GRPC);
        return props;
    }
}
