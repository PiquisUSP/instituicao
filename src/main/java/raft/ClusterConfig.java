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

/**
 * Configuração estática do cluster da instituição.
 *
 * <p>Cada nó tem uma porta de transporte Raft (gRPC). A porta HTTP (REST) é
 * gerenciada pelo Spring ({@code server.port}, definida por profile n1/n2/n3).
 * Todos os nós compartilham o mesmo {@link RaftGroupId} — é isso que os coloca
 * no mesmo grupo de consenso.
 *
 * <p>As portas Raft (7001-7003) são diferentes das do {@code servidor-de-chaves}
 * (6001-6003) para os dois clusters poderem rodar na mesma máquina sem colidir.
 *
 * <p>O <b>host</b> de cada nó é 127.0.0.1 por padrão (execução local). Em Docker,
 * defina {@code RAFT_HOST_N1}, {@code RAFT_HOST_N2}, {@code RAFT_HOST_N3} com os
 * nomes dos serviços/containers para os nós se encontrarem pela DNS interna.
 */
public final class ClusterConfig {

    private ClusterConfig() {
    }

    /** Identidade fixa do grupo Raft — precisa ser idêntica em todos os nós. */
    public static final RaftGroupId GROUP_ID =
            RaftGroupId.valueOf(UUID.fromString("9c1e2d3a-5b6f-4a7c-8d9e-0f1a2b3c4d5e"));

    /** id do nó -> porta do transporte Raft (gRPC). */
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

    /** Porta do transporte Raft deste nó (per-nó local; {@code RAFT_PORT} uniformiza em K8s). */
    public static int portaRaft(String id) {
        String env = System.getenv("RAFT_PORT");
        return (env != null && !env.isBlank()) ? Integer.parseInt(env.trim()) : PORTA_RAFT.get(id);
    }

    /** Diretório em disco com o log e os snapshots deste nó. */
    public static File diretorioStorage(String id) {
        return new File("raft-storage", id);
    }

    /**
     * Host onde o nó {@code id} é alcançável pelos demais. Local: 127.0.0.1.
     * Em Docker: defina RAFT_HOST_&lt;ID&gt; com o nome do serviço (ex.: n1).
     */
    private static String host(String id) {
        String env = System.getenv("RAFT_HOST_" + id.toUpperCase());
        return (env != null && !env.isBlank()) ? env.trim() : "127.0.0.1";
    }

    private static String enderecoRaft(String id) {
        return host(id) + ":" + portaRaft(id);
    }

    /** Constrói o grupo Raft com todos os peers configurados. */
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

    /** Propriedades do RaftServer deste nó: transporte gRPC, porta e diretório de log. */
    public static RaftProperties propriedadesServidor(String id) {
        RaftProperties props = new RaftProperties();

        RaftConfigKeys.Rpc.setType(props, SupportedRpcType.GRPC);
        GrpcConfigKeys.Server.setPort(props, portaRaft(id));

        RaftServerConfigKeys.setStorageDir(props, Collections.singletonList(diretorioStorage(id)));

        // Snapshots automáticos: a cada N entradas aplicadas, a StateMachine grava
        // o estado em disco (persistência) e o log pode ser compactado.
        RaftServerConfigKeys.Snapshot.setAutoTriggerEnabled(props, true);
        RaftServerConfigKeys.Snapshot.setAutoTriggerThreshold(props, 10L);

        return props;
    }

    /** Propriedades do RaftClient: só precisa saber o transporte (gRPC). */
    public static RaftProperties propriedadesCliente() {
        RaftProperties props = new RaftProperties();
        RaftConfigKeys.Rpc.setType(props, SupportedRpcType.GRPC);
        return props;
    }
}
