package pubsub;

import java.io.Serializable;

// Evento que uma instituição publica quando troca de líder. O Banco Central recebe
// e atualiza a tabela de roteamento. Precisa ser a mesma classe nos dois lados (RMI).
public class EventoLider implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String idInstituicao;
    private final String enderecoLider;
    private final long termo; // maior termo = evento mais recente

    public EventoLider(String idInstituicao, String enderecoLider, long termo) {
        this.idInstituicao = idInstituicao;
        this.enderecoLider = enderecoLider;
        this.termo = termo;
    }

    public String getIdInstituicao() {
        return idInstituicao;
    }

    public String getEnderecoLider() {
        return enderecoLider;
    }

    public long getTermo() {
        return termo;
    }

    @Override
    public String toString() {
        return "EventoLider{instituicao=" + idInstituicao
                + ", lider=" + enderecoLider + ", termo=" + termo + "}";
    }
}
