package estruturas.financeiro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Saldo - reserva, débito e crédito (base do 2PC)")
class SaldoTest {

    @Test
    @DisplayName("reservar com saldo suficiente move do disponível para o reservado")
    void reservarComSaldo() {
        Saldo saldo = new Saldo(10000);
        assertTrue(saldo.reservar(3000));
        assertEquals(7000, saldo.getValor());
        assertEquals(3000, saldo.getReservado());
    }

    @Test
    @DisplayName("reservar o saldo inteiro deixa o disponível zerado")
    void reservarValorExato() {
        Saldo saldo = new Saldo(5000);
        assertTrue(saldo.reservar(5000));
        assertEquals(0, saldo.getValor());
        assertEquals(5000, saldo.getReservado());
    }

    @Test
    @DisplayName("reservar mais do que tem falha e não muda o saldo")
    void reservarSemSaldo() {
        Saldo saldo = new Saldo(1000);
        assertFalse(saldo.reservar(1001));
        assertEquals(1000, saldo.getValor());
        assertEquals(0, saldo.getReservado());
    }

    @Test
    @DisplayName("reservar valor negativo falha")
    void reservarNegativo() {
        Saldo saldo = new Saldo(1000);
        assertFalse(saldo.reservar(-1));
        assertEquals(1000, saldo.getValor());
        assertEquals(0, saldo.getReservado());
    }

    @Test
    @DisplayName("liberar a reserva (ABORT) devolve o valor ao disponível")
    void reservarDepoisLiberar() {
        Saldo saldo = new Saldo(10000);
        saldo.reservar(4000);
        saldo.liberarReserva(4000);
        assertEquals(10000, saldo.getValor());
        assertEquals(0, saldo.getReservado());
    }

    @Test
    @DisplayName("efetivar o débito (COMMIT origem) tira o valor reservado de vez")
    void reservarDepoisEfetivar() {
        Saldo saldo = new Saldo(10000);
        saldo.reservar(4000);
        saldo.efetivarDebito(4000);
        assertEquals(6000, saldo.getValor());
        assertEquals(0, saldo.getReservado());
    }

    @Test
    @DisplayName("creditar (COMMIT destino) aumenta o disponível")
    void creditar() {
        Saldo saldo = new Saldo(1000);
        saldo.creditar(2500);
        assertEquals(3500, saldo.getValor());
    }
}
