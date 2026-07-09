package estruturas.db;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import estruturas.conta.ContaBancaria;
import estruturas.db.exceptions.conta.ContaJaRegistrada;

// Banco local da instituição: contas indexadas pelo número (String), criação e
// consulta em O(1). É o mesmo objeto usado pela StateMachine (escritas via Raft) e
// pelas leituras REST — ConcurrentHashMap porque o Ratis escreve numa thread e o
// REST lê em outra.
public class BancoDeDados {

    private static final Logger LOG = LoggerFactory.getLogger(BancoDeDados.class);

    private final ConcurrentHashMap<String, ContaBancaria> contas = new ConcurrentHashMap<>();

    public BancoDeDados() {
        this.carregarDados();
    }

    private void carregarDados() {
        // ponto de extensão: pré-carregar contas de um arquivo/seed, se precisar.
    }

    public void adicionarConta(ContaBancaria conta) throws ContaJaRegistrada {
        String numero = conta.getNumeroConta().getValor();
        if (this.contas.containsKey(numero)) {
            throw new ContaJaRegistrada();
        }
        this.contas.put(numero, conta);
        LOG.info("[DB] conta armazenada numeroConta={} (total de contas={})", numero, this.contas.size());
    }

    public ContaBancaria recuperarConta(String numeroConta) {
        if (numeroConta == null) {
            return null;
        }
        return this.contas.get(numeroConta);
    }

    public boolean existeConta(String numeroConta) {
        return numeroConta != null && this.contas.containsKey(numeroConta);
    }

    // --- snapshot (persistência do estado da StateMachine em disco) ---

    public Map<String, ContaBancaria> snapshot() {
        return new HashMap<>(this.contas);
    }

    public void restaurar(Map<String, ContaBancaria> dados) {
        this.contas.clear();
        if (dados != null) {
            this.contas.putAll(dados);
        }
    }
}
