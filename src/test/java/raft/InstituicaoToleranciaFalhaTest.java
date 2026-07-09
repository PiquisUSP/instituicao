package raft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

/**
 * Teste de tolerância a falhas do cluster de uma instituição: derruba nós enquanto
 * contas são criadas, para validar na prática o que a especificação promete (§8):
 *
 * <ul>
 *   <li><b>Tolerância à falha do líder</b> — o cluster sobrevive à queda do líder;</li>
 *   <li><b>Reeleição</b> — os sobreviventes elegem um novo líder automaticamente;</li>
 *   <li><b>Disponibilidade com maioria</b> — 2 de 3 nós ainda criam conta;</li>
 *   <li><b>Segurança</b> — conta commitada antes da falha não se perde;</li>
 *   <li><b>Sem maioria, não avança</b> — perdida a maioria, o Raft recusa novas
 *       escritas em vez de arriscar saldo/estado inconsistente (consistência > disponibilidade).</li>
 * </ul>
 *
 * <p>É um único cenário sequencial: as falhas mudam o estado do cluster de forma
 * destrutiva, então separar em vários métodos os tornaria dependentes de ordem.
 */
@DisplayName("Instituição - tolerância a falhas (derrubar nós valida o Raft das contas)")
class InstituicaoToleranciaFalhaTest {

    // CPFs válidos (o dígito verificador é conferido ao criar a conta).
    private static final String CPF_ALICE = "11144477735";
    private static final String CPF_BOB = "52998224725";
    private static final String CPF_CAROL = "39053344705";

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
    @Timeout(150)
    @DisplayName("Derrubar líder: reelege, continua criando conta, não perde dado; sem maioria, para")
    void derrubarLiderValidaPrincipios() throws Exception {
        // (1) Criação inicial confirmada e replicada nos 3 nós.
        assertEquals(200, criarConta(nos.get(0), "70000001", CPF_ALICE, "Alice"));
        assertTrue(esperarEmTodos(nos, "70000001", 30_000),
                "a conta inicial deveria replicar em todos os nós");

        // (2) Descobre o LÍDER e o derruba.
        NoInstituicao lider = esperarLider(nos, 30_000);
        assertNotNull(lider, "deveria haver um líder eleito");
        String idLider = lider.id();
        lider.close();
        nos.remove(lider);

        List<NoInstituicao> vivos = new ArrayList<>(nos); // os 2 sobreviventes
        assertEquals(2, vivos.size());

        // (3) REELEIÇÃO: os sobreviventes (maioria = 2 de 3) elegem um novo líder.
        NoInstituicao novoLider = esperarLider(vivos, 60_000);
        assertNotNull(novoLider, "os sobreviventes deveriam eleger um novo líder");
        assertNotEquals(idLider, novoLider.id(), "o novo líder deve ser diferente do que caiu");

        // (4) DISPONIBILIDADE: mesmo após a queda do líder, a criação de conta continua.
        assertEquals(200, criarConta(vivos.get(0), "70000002", CPF_BOB, "Bob"),
                "com maioria no ar, a criação de conta deve continuar funcionando");

        // (5) SEGURANÇA: a conta commitada antes da falha sobrevive, e a nova replica
        //     entre os sobreviventes.
        assertTrue(esperarEmTodos(vivos, "70000001", 30_000),
                "a conta commitada antes da falha não pode ser perdida");
        assertTrue(esperarEmTodos(vivos, "70000002", 30_000),
                "a nova conta deve replicar nos sobreviventes");

        // (6) PERDA DE MAIORIA: derruba mais um; sobra 1 de 3 (minoria). O Raft deve
        //     RECUSAR novas escritas (prioriza consistência a disponibilidade).
        NoInstituicao sobrevivente = vivos.get(0);
        NoInstituicao segundoAlvo = vivos.get(1);
        segundoAlvo.close();
        nos.remove(segundoAlvo);

        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<Integer> escrita = exec.submit(() -> criarConta(sobrevivente, "70000003", CPF_CAROL, "Carol"));
        boolean bloqueou = false;
        try {
            Integer status = escrita.get(15, TimeUnit.SECONDS);
            // Se por acaso retornar, não pode ter sido confirmada (200).
            assertNotEquals(200, status, "sem maioria não deveria confirmar a criação da conta");
        } catch (TimeoutException esperado) {
            // Comportamento esperado: a escrita fica bloqueada aguardando maioria.
            bloqueou = true;
        } finally {
            escrita.cancel(true);
            exec.shutdownNow();
        }
        assertTrue(bloqueou, "sem maioria, a criação de conta deveria ficar bloqueada (não confirmar)");
        assertFalse(sobrevivente.banco().existeConta("70000003"),
                "uma conta não confirmada não pode ter sido aplicada");
    }

    // ------------------------------------------------------------------

    private static int criarConta(NoInstituicao no, String numero, String cpf, String nome) {
        return no.aplicador().registrar(new ComandoCriarConta(numero, cpf, nome, "hash-teste"));
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

    private static void fecharSilencioso(NoInstituicao no) {
        try {
            no.close();
        } catch (Exception e) {
            // best-effort no teardown
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
