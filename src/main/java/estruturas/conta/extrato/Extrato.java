package estruturas.conta.extrato;

import estruturas.conta.extrato.transacao.ExtratoTransacao;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Extrato implements Serializable {
    private List<ExtratoTransacao> extratoTransacoes = new ArrayList<>();

    public void adicionarTransacao(ExtratoTransacao extratoTransacao) {
        extratoTransacoes.add(extratoTransacao);
    }

    public List<ExtratoTransacao> puxarExtrato() {
        return extratoTransacoes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Extrato extrato = (Extrato) o;
        return Objects.equals(extratoTransacoes, extrato.extratoTransacoes);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(extratoTransacoes);
    }
}
