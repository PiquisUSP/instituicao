package estruturas.instituicao;

import java.io.Serializable;

public class IdentificadorInstituicao implements Serializable {
    private static final long serialVersionUID = 1L;

    protected String valor;

    public IdentificadorInstituicao(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return this.valor;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        IdentificadorInstituicao outra = (IdentificadorInstituicao) obj;
        return this.valor.equals(outra.getValor());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + valor.hashCode();
    }

    @Override
    public String toString() {
        return "IdentificadorInstituicao=" + this.getValor();
    }
}