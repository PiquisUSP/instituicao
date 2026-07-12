package raft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import estruturas.conta.ContaBancaria;
import estruturas.transacao.TransacaoPendente;

@DisplayName("Transferência sob caos: derrubar nós no meio do 2PC (Raft de verdade)")
class TransferenciaCaosRaftTest {

    private static final String INST = "INST-0001";
    private static final String CPF_A = "11144477735";
    private static final String CPF_B = "52998224725";
    private static final long HORA = 1_700_000_000_000L;

    private static final List<NoInstituicao> nos = new ArrayList<>();

    @BeforeAll
    static void iniciarCluster() throws Exception {
        apagarStorage();
        for (String id : ClusterConfig.ids()) {
            NoInstituicao no = new NoInstituicao(id);
            no.iniciar();
            nos.add(no);
        }
    }

    @AfterAll
    static void pararCluster() throws Exception {
        for (NoInstituicao no : nos) {
            fecharSilencioso(no);
        }
        nos.clear();
        apagarStorage();
    }

    @Test
    @Timeout(200)
    @DisplayName("Derruba o líder no meio da transferência: a reserva sobrevive e o COMMIT completa; sem maioria, para")
    void caosNaTransferencia() throws Exception {
        // (1) Duas contas fundadas, replicadas nos 3 nós.
        assertEquals(200, criarConta(nos.get(0), "111", CPF_A, "Alice", 10000));
        assertEquals(200, criarConta(nos.get(0), "222", CPF_B, "Bob", 0));
        assertTrue(esperarEmTodos(nos, "111", 30_000));
        assertTrue(esperarEmTodos(nos, "222", 30_000));

        // (2) PREPARE: reserva 3000 da conta de origem, replicado pela maioria.
        UUID tx = UUID.randomUUID();
        TransacaoPendente pendente = new TransacaoPendente(tx, INST, "111", INST, "222", 3000, HORA, true, true);
        assertEquals(200, nos.get(0).aplicador().registrar(new ComandoPreparar(pendente)));
        assertTrue(esperarReservado(nos, "111", 3000, 30_000),
                "a reserva deve estar replicada em todos os nós antes da queda");

        // (3) CAOS: derruba o LÍDER no meio da transação (entre o PREPARE e o COMMIT).
        NoInstituicao lider = esperarLider(nos, 30_000);
        assertNotNull(lider, "deveria haver um líder");
        String idLider = lider.id();
        lider.close();
        nos.remove(lider);
        List<NoInstituicao> vivos = new ArrayList<>(nos);
        assertEquals(2, vivos.size());

        // (4) Os sobreviventes (maioria = 2 de 3) reelegem, e a RESERVA SOBREVIVEU à queda.
        NoInstituicao novoLider = esperarLider(vivos, 60_000);
        assertNotNull(novoLider, "os sobreviventes deveriam eleger um novo líder");
        assertNotEquals(idLider, novoLider.id());
        assertEquals(3000, novoLider.banco().recuperarConta("111").getSaldo().getReservado(),
                "a reserva feita antes da queda não pode sumir");
        assertNotNull(novoLider.banco().recuperarPendente(tx),
                "a transação pendente tem que sobreviver à queda do líder");

        // (5) COMMIT depois da queda: a transferência completa mesmo assim (debita origem,
        //     credita destino), consistente entre os sobreviventes.
        assertEquals(200, vivos.get(0).aplicador().registrar(new ComandoComitar(tx)));
        assertTrue(esperarSaldo(vivos, "111", 7000, 30_000), "origem deveria estar debitada");
        assertTrue(esperarSaldo(vivos, "222", 3000, 30_000), "destino deveria estar creditado");

        // (6) CAOS TOTAL: derruba mais um; sobra 1 de 3 (minoria). Uma nova transferência
        //     deve ser RECUSADA (fica bloqueada) — consistência acima de disponibilidade.
        NoInstituicao sobrevivente = vivos.get(0);
        NoInstituicao segundoAlvo = vivos.get(1);
        segundoAlvo.close();
        nos.remove(segundoAlvo);

        UUID tx2 = UUID.randomUUID();
        TransacaoPendente pendente2 = new TransacaoPendente(tx2, INST, "111", INST, "222", 1000, HORA, true, true);
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<Integer> escrita = exec.submit(() -> sobrevivente.aplicador().registrar(new ComandoPreparar(pendente2)));
        boolean bloqueou = false;
        try {
            Integer status = escrita.get(15, TimeUnit.SECONDS);
            assertNotEquals(200, status, "sem maioria não deveria confirmar a reserva");
        } catch (TimeoutException esperado) {
            bloqueou = true;
        } finally {
            escrita.cancel(true);
            exec.shutdownNow();
        }
        assertTrue(bloqueou || sobrevivente.banco().recuperarPendente(tx2) == null,
                "sem maioria, a nova transferência não pode avançar");
    }

    // ------------------------------------------------------------------

    private static int criarConta(NoInstituicao no, String numero, String cpf, String nome, long saldo) {
        return no.aplicador().registrar(new ComandoCriarConta(numero, cpf, nome, "hash-teste", saldo));
    }

    private static NoInstituicao esperarLider(List<NoInstituicao> candidatos, long timeoutMs)
            throws InterruptedException {
        long limite = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < limite) {
            for (NoInstituicao no : candidatos) {
                if (no.isLider()) {
                    return no;
                }
            }
            Thread.sleep(200);
        }
        return null;
    }

    private static boolean esperarEmTodos(List<NoInstituicao> lista, String numeroConta, long timeoutMs)
            throws InterruptedException {
        long limite = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < limite) {
            boolean todos = true;
            for (NoInstituicao no : lista) {
                if (!no.banco().existeConta(numeroConta)) {
                    todos = false;
                    break;
                }
            }
            if (todos) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }

    private static boolean esperarReservado(List<NoInstituicao> lista, String conta, long valor, long timeoutMs)
            throws InterruptedException {
        return esperarNaConta(lista, conta, timeoutMs, c -> c.getSaldo().getReservado() == valor);
    }

    private static boolean esperarSaldo(List<NoInstituicao> lista, String conta, long valor, long timeoutMs)
            throws InterruptedException {
        return esperarNaConta(lista, conta, timeoutMs, c -> c.getSaldo().getValor() == valor);
    }

    private interface CondicaoConta {
        boolean vale(ContaBancaria conta);
    }

    private static boolean esperarNaConta(List<NoInstituicao> lista, String conta, long timeoutMs, CondicaoConta cond)
            throws InterruptedException {
        long limite = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < limite) {
            boolean todos = true;
            for (NoInstituicao no : lista) {
                ContaBancaria c = no.banco().recuperarConta(conta);
                if (c == null || !cond.vale(c)) {
                    todos = false;
                    break;
                }
            }
            if (todos) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }

    private static void fecharSilencioso(NoInstituicao no) {
        try {
            no.close();
        } catch (Exception e) {
        }
    }

    private static void apagarStorage() throws Exception {
        Path dir = new File("raft-storage").toPath();
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}
