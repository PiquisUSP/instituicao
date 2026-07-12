package raft;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.CompletableFuture;

import org.apache.ratis.proto.RaftProtos.LogEntryProto;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientRequest;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.protocol.TermIndex;
import org.apache.ratis.server.raftlog.RaftLog;
import org.apache.ratis.server.storage.RaftStorage;
import org.apache.ratis.statemachine.SnapshotInfo;
import org.apache.ratis.statemachine.StateMachineStorage;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.statemachine.impl.BaseStateMachine;
import org.apache.ratis.statemachine.impl.SimpleStateMachineStorage;
import org.apache.ratis.statemachine.impl.SingleFileSnapshotInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import estruturas.db.BancoDeDados;
import estruturas.db.EstadoBanco;

public class InstituicaoStateMachine extends BaseStateMachine {

    private static final Logger LOG = LoggerFactory.getLogger(InstituicaoStateMachine.class);

    private final SimpleStateMachineStorage storage = new SimpleStateMachineStorage();

    private final BancoDeDados db;

    public InstituicaoStateMachine(BancoDeDados db) {
        this.db = db;
    }

    @Override
    public void initialize(RaftServer server, RaftGroupId groupId, RaftStorage raftStorage) throws IOException {
        super.initialize(server, groupId, raftStorage);
        this.storage.init(raftStorage);
        carregarSnapshot(storage.getLatestSnapshot());
    }

    @Override
    public void reinitialize() throws IOException {
        carregarSnapshot(storage.getLatestSnapshot());
    }

    @Override
    public StateMachineStorage getStateMachineStorage() {
        return storage;
    }

    @Override
    public SnapshotInfo getLatestSnapshot() {
        return storage.getLatestSnapshot();
    }

    @Override
    public TransactionContext startTransaction(RaftClientRequest request) throws IOException {
        return TransactionContext.newBuilder()
                .setStateMachine(this)
                .setClientRequest(request)
                .setLogData(request.getMessage().getContent())
                .build();
    }

    @Override
    public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
        final LogEntryProto entry = trx.getLogEntry();
        final String dados = entry.getStateMachineLogEntry().getLogData().toStringUtf8();

        int status;
        Comando comando = null;
        try {
            comando = Comandos.desserializar(dados);
            status = comando.aplicar(db);
        } catch (Exception e) {
            LOG.error("Erro ao aplicar entrada {}", entry.getIndex(), e);
            status = 500;
        }

        updateLastAppliedTermIndex(entry.getTerm(), entry.getIndex());

        LOG.info("[apply] index={} {} -> {}", entry.getIndex(), comando, status);

        return CompletableFuture.completedFuture(Message.valueOf(Integer.toString(status)));
    }

    @Override
    public long takeSnapshot() {
        final TermIndex ultimo = getLastAppliedTermIndex();
        final EstadoBanco copia = db.snapshot();

        final File arquivo = storage.getSnapshotFile(ultimo.getTerm(), ultimo.getIndex());
        LOG.info("[snapshot] gravando {} ({} contas, {} pendentes)",
                arquivo.getName(), copia.contas().size(), copia.pendentes().size());

        try (ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(arquivo)))) {
            out.writeObject(copia);
        } catch (IOException e) {
            LOG.warn("[snapshot] falha ao gravar {}", arquivo, e);
        }

        return ultimo.getIndex();
    }

    private long carregarSnapshot(SingleFileSnapshotInfo snapshot) throws IOException {
        if (snapshot == null) {
            return RaftLog.INVALID_LOG_INDEX;
        }
        final File arquivo = snapshot.getFile().getPath().toFile();
        if (!arquivo.exists()) {
            return RaftLog.INVALID_LOG_INDEX;
        }

        final TermIndex ultimo = SimpleStateMachineStorage.getTermIndexFromSnapshotFile(arquivo);
        try (ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(arquivo)))) {
            EstadoBanco dados = (EstadoBanco) in.readObject();
            db.restaurar(dados);
            setLastAppliedTermIndex(ultimo);
            LOG.info("[snapshot] restaurado index={} ({} contas, {} pendentes)",
                    ultimo.getIndex(), dados.contas().size(), dados.pendentes().size());
        } catch (ClassNotFoundException e) {
            throw new IOException("Snapshot corrompido: " + arquivo, e);
        }

        return ultimo.getIndex();
    }
}
