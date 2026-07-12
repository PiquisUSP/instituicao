package rmi.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.rmi.server.UnicastRemoteObject;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import estruturas.conta.ContaBancaria;
import estruturas.db.BancoDeDados;
import raft.AplicadorLocal;

@DisplayName("Transferência entre instituições (participantes reais, sem RMI/Raft)")
class TransferenciaIntegracaoTest {

    private static final String INST_A = "INST-0001";
    private static final String INST_B = "INST-0002";
    private static final String CPF_A = "11144477735";
    private static final String CPF_B = "52998224725";

    private BancoDeDados bancoA;
    private BancoDeDados bancoB;
    private TransacaoService origem;
    private TransacaoService destino;

    @BeforeEach
    void preparar() throws Exception {
        bancoA = new BancoDeDados(INST_A);
        bancoA.adicionarConta(new ContaBancaria("111", CPF_A, "Alice", "hash", 10000));
        bancoB = new BancoDeDados(INST_B);
        bancoB.adicionarConta(new ContaBancaria("222", CPF_B, "Bob", "hash", 500));

        origem = new TransacaoService(new AplicadorLocal(bancoA), bancoA, INST_A, 0);
        destino = new TransacaoService(new AplicadorLocal(bancoB), bancoB, INST_B, 0);
    }

    @AfterEach
    void encerrar() throws Exception {
        UnicastRemoteObject.unexportObject(origem, true);
        UnicastRemoteObject.unexportObject(destino, true);
    }

    private long saldoA(String conta) {
        return bancoA.recuperarConta(conta).getSaldo().getValor();
    }

    private long saldoB(String conta) {
        return bancoB.recuperarConta(conta).getSaldo().getValor();
    }

    @Test
    @DisplayName("Transferência normal: ambos preparam, COMMIT debita a origem e credita o destino")
    void transferenciaCompleta() throws Exception {
        UUID id = UUID.randomUUID();

        assertTrue(origem.prepare(id, INST_A, "111", INST_B, "222", 3000));
        assertTrue(destino.prepare(id, INST_A, "111", INST_B, "222", 3000));

        assertTrue(origem.commit(id));
        assertTrue(destino.commit(id));

        assertEquals(7000, saldoA("111"));
        assertEquals(0, bancoA.recuperarConta("111").getSaldo().getReservado());
        assertEquals(3500, saldoB("222"));
        assertEquals(1, bancoA.recuperarConta("111").getExtrato().puxarExtrato().size());
        assertEquals(1, bancoB.recuperarConta("222").getExtrato().puxarExtrato().size());
    }

    @Test
    @DisplayName("Se o destino recusa (conta não existe), o ABORT libera a reserva da origem")
    void transferenciaAbortada() throws Exception {
        UUID id = UUID.randomUUID();

        assertTrue(origem.prepare(id, INST_A, "111", INST_B, "999", 3000));
        assertFalse(destino.prepare(id, INST_A, "111", INST_B, "999", 3000));

        origem.cancel(id);
        destino.cancel(id);

        assertEquals(10000, saldoA("111"));
        assertEquals(0, bancoA.recuperarConta("111").getSaldo().getReservado());
        assertNull(bancoA.recuperarPendente(id));
    }

    @Test
    @DisplayName("Origem sem saldo vota não; ninguém perde dinheiro")
    void origemSemSaldo() throws Exception {
        UUID id = UUID.randomUUID();
        assertFalse(origem.prepare(id, INST_A, "111", INST_B, "222", 999999));
        assertEquals(10000, saldoA("111"));
        assertNull(bancoA.recuperarPendente(id));
    }

    @Test
    @DisplayName("Instituição que não é origem nem destino vota não")
    void instituicaoNaoEnvolvida() throws Exception {
        BancoDeDados bancoC = new BancoDeDados("INST-0003");
        TransacaoService fora = new TransacaoService(new AplicadorLocal(bancoC), bancoC, "INST-0003", 0);
        try {
            UUID id = UUID.randomUUID();
            assertFalse(fora.prepare(id, INST_A, "111", INST_B, "222", 3000));
            assertNull(bancoC.recuperarPendente(id));
        } finally {
            UnicastRemoteObject.unexportObject(fora, true);
        }
    }

    @Test
    @DisplayName("Recuperação: a reserva sobrevive entre o PREPARE e um COMMIT que chega depois")
    void commitAtrasado() throws Exception {
        UUID id = UUID.randomUUID();
        assertTrue(origem.prepare(id, INST_A, "111", INST_B, "222", 4000));
        assertEquals(4000, bancoA.recuperarConta("111").getSaldo().getReservado());

        assertTrue(origem.commit(id));
        assertEquals(6000, saldoA("111"));
        assertEquals(0, bancoA.recuperarConta("111").getSaldo().getReservado());
    }

    @Test
    @DisplayName("PREPARE e COMMIT repetidos (reenvio) não duplicam o efeito")
    void reenvioIdempotente() throws Exception {
        UUID id = UUID.randomUUID();
        assertTrue(origem.prepare(id, INST_A, "111", INST_B, "222", 3000));
        assertTrue(origem.prepare(id, INST_A, "111", INST_B, "222", 3000));
        assertTrue(origem.commit(id));
        assertTrue(origem.commit(id));
        assertEquals(7000, saldoA("111"));
        assertEquals(1, bancoA.recuperarConta("111").getExtrato().puxarExtrato().size());
    }
}
