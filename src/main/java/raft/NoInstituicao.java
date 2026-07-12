package raft;

import java.io.File;
import java.io.IOException;

import org.apache.ratis.client.RaftClient;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.storage.RaftStorage;

import estruturas.db.BancoDeDados;

public class NoInstituicao implements AutoCloseable {
    private final String id;
    private final BancoDeDados db;
    private final RaftServer raftServer;
    private final RaftClient raftClient;
    private final AplicadorRaft aplicador;

    public NoInstituicao(String id) throws IOException {
        if (!ClusterConfig.noValido(id)) {
            throw new IllegalArgumentException(
                    "Nó desconhecido: '" + id + "'. Válidos: " + ClusterConfig.ids());
        }

        this.id = id;
        this.db = new BancoDeDados(id);

        RaftGroup grupo = ClusterConfig.grupo();

        File storageDir = ClusterConfig.diretorioStorage(id);
        boolean temStorage = storageDir.isDirectory()
                && storageDir.list() != null && storageDir.list().length > 0;
        RaftStorage.StartupOption opcao = temStorage
                ? RaftStorage.StartupOption.RECOVER
                : RaftStorage.StartupOption.FORMAT;

        this.raftServer = RaftServer.newBuilder()
                .setServerId(RaftPeerId.valueOf(id))
                .setGroup(grupo)
                .setOption(opcao)
                .setProperties(ClusterConfig.propriedadesServidor(id))
                .setStateMachine(new InstituicaoStateMachine(db))
                .build();

        this.raftClient = RaftClient.newBuilder()
                .setProperties(ClusterConfig.propriedadesCliente())
                .setRaftGroup(grupo)
                .build();

        this.aplicador = new AplicadorRaft(raftClient);
    }

    public void iniciar() throws IOException {
        raftServer.start();
    }

    public AplicadorRaft aplicador() {
        return aplicador;
    }

    public BancoDeDados banco() {
        return db;
    }

    public String id() {
        return id;
    }

    public boolean isLider() {
        try {
            return raftServer.getDivision(ClusterConfig.GROUP_ID).getInfo().isLeader();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void close() throws IOException {
        raftClient.close();
        raftServer.close();
    }
}
