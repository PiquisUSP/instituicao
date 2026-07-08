package raft;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
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

import estruturas.conta.ContaBancaria;
import estruturas.db.BancoDeDados;
import estruturas.db.exceptions.conta.ContaJaRegistrada;

/**
 * StateMachine do Raft: a "máquina de estados replicada".
 *
 * <p>O Ratis garante que todos os nós apliquem <b>exatamente as mesmas entradas
 * na mesma ordem</b>. Cada entrada commitada vira uma inserção de conta no
 * {@link BancoDeDados}. Como a operação é determinística (mesmo comando + mesmo
 * estado anterior => mesmo resultado), todos os nós convergem para o mesmo banco.
 *
 * <p><b>Persistência:</b> o log do Raft já é gravado em disco pelo Ratis; além
 * dele, {@link #takeSnapshot()} grava periodicamente o estado do banco, e
 * {@link #initialize}/{@link #reinitialize} o recarregam ao subir.
 */
public class InstituicaoStateMachine extends BaseStateMachine {

    private static final Logger LOG = LoggerFactory.getLogger(InstituicaoStateMachine.class);

    private final SimpleStateMachineStorage storage = new SimpleStateMachineStorage();

    /** Estado replicado: o mesmo tipo de banco usado no modo local. */
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

    /**
     * Expõe o storage ao servidor. Sem isto, o Ratis não sabe que existem
     * snapshots e reproduz o log desde o início ao reiniciar.
     */
    @Override
    public StateMachineStorage getStateMachineStorage() {
        return storage;
    }

    @Override
    public SnapshotInfo getLatestSnapshot() {
        return storage.getLatestSnapshot();
    }

    /**
     * Coloca o conteúdo da requisição do cliente como dados da entrada de log.
     * É o que {@link #applyTransaction} vai ler depois de a entrada ser commitada.
     */
    @Override
    public TransactionContext startTransaction(RaftClientRequest request) throws IOException {
        return TransactionContext.newBuilder()
                .setStateMachine(this)
                .setClientRequest(request)
                .setLogData(request.getMessage().getContent())
                .build();
    }

    /**
     * Aplica uma entrada já commitada ao banco. Executado em <b>todos</b> os nós,
     * na mesma ordem — é o que mantém as réplicas consistentes.
     */
    @Override
    public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
        final LogEntryProto entry = trx.getLogEntry();
        final String dados = entry.getStateMachineLogEntry().getLogData().toStringUtf8();

        int status;
        ComandoCriarConta comando = null;
        try {
            comando = ComandoCriarConta.desserializar(dados);
            ContaBancaria conta = comando.reconstruirConta();
            db.adicionarConta(conta);
            status = 200;
        } catch (ContaJaRegistrada e) {
            status = 403;
        } catch (Exception e) {
            LOG.error("Erro ao aplicar entrada {}", entry.getIndex(), e);
            status = 500;
        }

        // Avança o índice aplicado da StateMachine (usado em snapshots/reinício).
        updateLastAppliedTermIndex(entry.getTerm(), entry.getIndex());

        LOG.info("[applyTransaction] index={} {} -> {}", entry.getIndex(), comando, status);

        return CompletableFuture.completedFuture(Message.valueOf(Integer.toString(status)));
    }

    /**
     * Grava o estado atual do banco num arquivo de snapshot, nomeado pelo
     * (term, index) da última entrada aplicada. Chamado pelo Ratis periodicamente.
     *
     * @return o índice até o qual este snapshot cobre o log.
     */
    @Override
    public long takeSnapshot() {
        final TermIndex ultimo = getLastAppliedTermIndex();
        final Map<String, ContaBancaria> copia = db.snapshot();

        final File arquivo = storage.getSnapshotFile(ultimo.getTerm(), ultimo.getIndex());
        LOG.info("[takeSnapshot] gravando snapshot {} ({} contas)", arquivo.getName(), copia.size());

        try (ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(arquivo)))) {
            out.writeObject(copia);
        } catch (IOException e) {
            LOG.warn("[takeSnapshot] falha ao gravar snapshot {}", arquivo, e);
        }

        return ultimo.getIndex();
    }

    /** Recarrega o estado do banco a partir do snapshot mais recente (se houver). */
    @SuppressWarnings("unchecked")
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
            Map<String, ContaBancaria> dados = (Map<String, ContaBancaria>) in.readObject();
            db.restaurar(dados);
            setLastAppliedTermIndex(ultimo);
            LOG.info("[carregarSnapshot] restaurado do snapshot index={} ({} contas)",
                    ultimo.getIndex(), dados.size());
        } catch (ClassNotFoundException e) {
            throw new IOException("Snapshot corrompido: " + arquivo, e);
        }

        return ultimo.getIndex();
    }
}
