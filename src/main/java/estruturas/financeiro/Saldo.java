package estruturas.financeiro;

import java.io.Serializable;
import java.util.Objects;

public class Saldo implements Serializable {

    private static final long serialVersionUID = 1L;

    private long valor = 0L;
    private long reservado = 0L;

    public Saldo(long valor) {
        this.valor = valor;
    }

    public long getValor() {
        return valor;
    }

    public long getReservado() {
        return reservado;
    }

    public boolean reservar(long centavos) {
        if (centavos < 0 || valor < centavos) {
            return false;
        }
        valor -= centavos;
        reservado += centavos;
        return true;
    }

    public void liberarReserva(long centavos) {
        reservado -= centavos;
        valor += centavos;
    }

    public void efetivarDebito(long centavos) {
        reservado -= centavos;
    }

    public void creditar(long centavos) {
        valor += centavos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Saldo saldo = (Saldo) o;
        return valor == saldo.valor;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(valor);
    }

    public void somar(long centavos) {
        this.valor += centavos;
    }

    public void subtrair(long centavos) {
        this.valor -= centavos;
    }
}
