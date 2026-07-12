package estruturas.db;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import estruturas.conta.ContaBancaria;
import estruturas.transacao.TransacaoPendente;

public class EstadoBanco implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, ContaBancaria> contas;
    private final Map<UUID, TransacaoPendente> pendentes;

    public EstadoBanco(Map<String, ContaBancaria> contas, Map<UUID, TransacaoPendente> pendentes) {
        this.contas = new HashMap<>(contas);
        this.pendentes = new HashMap<>(pendentes);
    }

    public Map<String, ContaBancaria> contas() {
        return contas;
    }

    public Map<UUID, TransacaoPendente> pendentes() {
        return pendentes;
    }
}
