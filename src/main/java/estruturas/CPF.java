package estruturas;

import java.io.Serializable;
import java.util.Objects;

// Serializable: faz parte da ContaBancaria gravada nos snapshots do Raft.
public class CPF implements Serializable {
    private static final long serialVersionUID = 1L;

    private String valor;

    public String getValor() {
        return valor;
    }

    public CPF(String cpf) {
        if (isValid(cpf)) {
            this.valor = cpf;
        }
    }

    protected boolean isValid(String cpf) {
        if (cpf == null) {
            return false;
        }

        String cpfLimpo = cpf.replaceAll("\\D", "");

        if (cpfLimpo.length() != 11) {
            return false;
        }

        if (cpfLimpo.matches("(\\d)\\1{10}")) {
            return false;
        }

        try {
            // Cálculo do 1º dígito verificador
            int soma = 0;
            for (int i = 0; i < 9; i++) {
                int numero = cpfLimpo.charAt(i) - '0';
                soma += numero * (10 - i);
            }
            int peso1 = 11 - (soma % 11);
            int digito1 = (peso1 > 9) ? 0 : peso1;

            // Cálculo do 2º dígito verificador
            soma = 0;
            for (int i = 0; i < 10; i++) {
                int numero = cpfLimpo.charAt(i) - '0';
                soma += numero * (11 - i);
            }
            int peso2 = 11 - (soma % 11);
            int digito2 = (peso2 > 9) ? 0 : peso2;

            // Verifica se os dígitos calculados conferem com os informados
            int digitoInformado1 = cpfLimpo.charAt(9) - '0';
            int digitoInformado2 = cpfLimpo.charAt(10) - '0';

            return (digitoInformado1 == digito1) && (digitoInformado2 == digito2);

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CPF cpf = (CPF) o;
        return Objects.equals(valor, cpf.valor);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(valor);
    }
}
