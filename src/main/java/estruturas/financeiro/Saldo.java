package estruturas.financeiro;

import java.io.Serializable;
import java.util.Objects;

public class Saldo implements Serializable {
    private long valor = 0L;

    public Saldo(long valor) {
        this.valor = valor;
    }

    public long getValor() {
        return valor;
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
