package estruturas.transacao;

import java.io.Serializable;
import java.util.UUID;

public class TransacaoPendente implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String idInstOrigem;
    private final String contaOrigem;
    private final String idInstDestino;
    private final String contaDestino;
    private final long valorCentavos;
    private final long horaEpochMillis;
    private final boolean origemLocal;
    private final boolean destinoLocal;

    public TransacaoPendente(UUID id, String idInstOrigem, String contaOrigem, String idInstDestino,
            String contaDestino, long valorCentavos, long horaEpochMillis,
            boolean origemLocal, boolean destinoLocal) {
        this.id = id;
        this.idInstOrigem = idInstOrigem;
        this.contaOrigem = contaOrigem;
        this.idInstDestino = idInstDestino;
        this.contaDestino = contaDestino;
        this.valorCentavos = valorCentavos;
        this.horaEpochMillis = horaEpochMillis;
        this.origemLocal = origemLocal;
        this.destinoLocal = destinoLocal;
    }

    public UUID getId() {
        return id;
    }

    public String getIdInstOrigem() {
        return idInstOrigem;
    }

    public String getContaOrigem() {
        return contaOrigem;
    }

    public String getIdInstDestino() {
        return idInstDestino;
    }

    public String getContaDestino() {
        return contaDestino;
    }

    public long getValorCentavos() {
        return valorCentavos;
    }

    public long getHoraEpochMillis() {
        return horaEpochMillis;
    }

    public boolean isOrigemLocal() {
        return origemLocal;
    }

    public boolean isDestinoLocal() {
        return destinoLocal;
    }

    @Override
    public String toString() {
        return "TransacaoPendente{id=" + id + ", " + idInstOrigem + "/" + contaOrigem
                + " -> " + idInstDestino + "/" + contaDestino + ", valor=" + valorCentavos + "c}";
    }
}
