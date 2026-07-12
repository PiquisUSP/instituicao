package raft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import estruturas.conta.ContaBancaria;
import estruturas.db.BancoDeDados;
import estruturas.transacao.TransacaoPendente;

@DisplayName("Comandos de transferência (PREPARE/COMMIT/ABORT) sobre o banco local")
class ComandoTransferenciaTest {

    private static final String INST_A = "INST-0001";
    private static final String INST_B = "INST-0002";
    private static final String CPF_A = "11144477735";
    private static final String CPF_B = "52998224725";
    private static final long HORA = 1_700_000_000_000L;

    private BancoDeDados db;

    @BeforeEach
    void preparar() throws Exception {
        db = new BancoDeDados(INST_A);
        db.adicionarConta(new ContaBancaria("111", CPF_A, "Alice", "hash", 10000));
        db.adicionarConta(new ContaBancaria("222", CPF_B, "Bob", "hash", 500));
    }

    private TransacaoPendente pendente(UUID id, String origem, String destino, long valor,
            boolean origemLocal, boolean destinoLocal) {
        return new TransacaoPendente(id, INST_A, origem, INST_B, destino, valor, HORA, origemLocal, destinoLocal);
    }

    private long saldo(String conta) {
        return db.recuperarConta(conta).getSaldo().getValor();
    }

    private long reservado(String conta) {
        return db.recuperarConta(conta).getSaldo().getReservado();
    }

    private int extratos(String conta) {
        return db.recuperarConta(conta).getExtrato().puxarExtrato().size();
    }

    @Test
    @DisplayName("PREPARE na origem reserva o valor e registra a pendência")
    void prepareOrigemReserva() {
        UUID id = UUID.randomUUID();
        int status = new ComandoPreparar(pendente(id, "111", "222", 3000, true, false)).aplicar(db);
        assertEquals(200, status);
        assertEquals(7000, saldo("111"));
        assertEquals(3000, reservado("111"));
        assertNotNull(db.recuperarPendente(id));
    }

    @Test
    @DisplayName("PREPARE na origem sem saldo suficiente é recusado (403) e não mexe no saldo")
    void prepareOrigemSemSaldo() {
        UUID id = UUID.randomUUID();
        int status = new ComandoPreparar(pendente(id, "111", "222", 20000, true, false)).aplicar(db);
        assertEquals(403, status);
        assertEquals(10000, saldo("111"));
        assertEquals(0, reservado("111"));
        assertNull(db.recuperarPendente(id));
    }

    @Test
    @DisplayName("PREPARE numa conta de origem inexistente devolve 404")
    void prepareOrigemInexistente() {
        UUID id = UUID.randomUUID();
        int status = new ComandoPreparar(pendente(id, "999", "222", 100, true, false)).aplicar(db);
        assertEquals(404, status);
    }

    @Test
    @DisplayName("PREPARE no destino só valida a conta, sem mexer no saldo")
    void prepareDestinoValida() {
        UUID id = UUID.randomUUID();
        int status = new ComandoPreparar(pendente(id, "888", "222", 3000, false, true)).aplicar(db);
        assertEquals(200, status);
        assertEquals(500, saldo("222"));
        assertNotNull(db.recuperarPendente(id));
    }

    @Test
    @DisplayName("PREPARE num destino inexistente devolve 404")
    void prepareDestinoInexistente() {
        UUID id = UUID.randomUUID();
        int status = new ComandoPreparar(pendente(id, "888", "999", 100, false, true)).aplicar(db);
        assertEquals(404, status);
    }

    @Test
    @DisplayName("PREPARE repetido é idempotente: não reserva duas vezes")
    void prepareIdempotente() {
        UUID id = UUID.randomUUID();
        ComandoPreparar cmd = new ComandoPreparar(pendente(id, "111", "222", 3000, true, false));
        assertEquals(200, cmd.aplicar(db));
        assertEquals(200, cmd.aplicar(db));
        assertEquals(3000, reservado("111"));
        assertEquals(7000, saldo("111"));
    }

    @Test
    @DisplayName("COMMIT na origem efetiva o débito, lança no extrato e limpa a pendência")
    void commitOrigem() {
        UUID id = UUID.randomUUID();
        new ComandoPreparar(pendente(id, "111", "222", 3000, true, false)).aplicar(db);
        int status = new ComandoComitar(id).aplicar(db);
        assertEquals(200, status);
        assertEquals(7000, saldo("111"));
        assertEquals(0, reservado("111"));
        assertEquals(1, extratos("111"));
        assertNull(db.recuperarPendente(id));
    }

    @Test
    @DisplayName("COMMIT no destino credita o valor e lança no extrato")
    void commitDestino() {
        UUID id = UUID.randomUUID();
        new ComandoPreparar(pendente(id, "888", "222", 3000, false, true)).aplicar(db);
        int status = new ComandoComitar(id).aplicar(db);
        assertEquals(200, status);
        assertEquals(3500, saldo("222"));
        assertEquals(1, extratos("222"));
    }

    @Test
    @DisplayName("COMMIT sem pendência (já comitada ou reenvio) é no-op com sucesso")
    void commitSemPendencia() {
        int status = new ComandoComitar(UUID.randomUUID()).aplicar(db);
        assertEquals(200, status);
    }

    @Test
    @DisplayName("COMMIT repetido não debita de novo (idempotente)")
    void commitIdempotente() {
        UUID id = UUID.randomUUID();
        new ComandoPreparar(pendente(id, "111", "222", 3000, true, false)).aplicar(db);
        new ComandoComitar(id).aplicar(db);
        assertEquals(200, new ComandoComitar(id).aplicar(db));
        assertEquals(7000, saldo("111"));
        assertEquals(1, extratos("111"));
    }

    @Test
    @DisplayName("ABORT na origem libera a reserva e limpa a pendência")
    void cancelOrigem() {
        UUID id = UUID.randomUUID();
        new ComandoPreparar(pendente(id, "111", "222", 3000, true, false)).aplicar(db);
        int status = new ComandoCancelar(id).aplicar(db);
        assertEquals(200, status);
        assertEquals(10000, saldo("111"));
        assertEquals(0, reservado("111"));
        assertNull(db.recuperarPendente(id));
    }

    @Test
    @DisplayName("ABORT sem pendência é no-op com sucesso")
    void cancelSemPendencia() {
        assertEquals(200, new ComandoCancelar(UUID.randomUUID()).aplicar(db));
    }

    @Test
    @DisplayName("Transferência interna (mesma instituição): debita a origem e credita o destino")
    void transferenciaInterna() {
        UUID id = UUID.randomUUID();
        new ComandoPreparar(new TransacaoPendente(id, INST_A, "111", INST_A, "222", 3000, HORA, true, true)).aplicar(db);
        new ComandoComitar(id).aplicar(db);
        assertEquals(7000, saldo("111"));
        assertEquals(3500, saldo("222"));
        assertEquals(1, extratos("111"));
        assertEquals(1, extratos("222"));
    }
}
