package estruturas.db;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import estruturas.conta.ContaBancaria;
import estruturas.db.exceptions.conta.ContaJaRegistrada;

/**
 * Banco de dados local da instituição: guarda as contas bancárias indexadas pelo
 * <b>número da conta</b> (String), deixando criação e consulta em O(1).
 *
 * <p>É o mesmo objeto usado pela {@code InstituicaoStateMachine} (escritas via
 * Raft) e pelo controller REST (leituras locais). {@code ConcurrentHashMap}
 * porque o Ratis aplica entradas numa thread e o REST lê em outra.
 */
public class BancoDeDados {

    private static final Logger LOG = LoggerFactory.getLogger(BancoDeDados.class);

    private final ConcurrentHashMap<String, ContaBancaria> contas = new ConcurrentHashMap<>();

    public BancoDeDados() {
        this.carregarDados();
    }

    private void carregarDados() {
        // Ponto de extensão: pré-carregar contas de um arquivo/seed, se necessário.
    }

    public void adicionarConta(ContaBancaria conta) throws ContaJaRegistrada {
        String numero = conta.getNumeroConta().getValor();
        if (this.contas.containsKey(numero)) {
            throw new ContaJaRegistrada();
        }
        this.contas.put(numero, conta);
        LOG.info("[DB] conta armazenada numeroConta={} (total de contas={})", numero, this.contas.size());
    }

    /** Consulta O(1) pelo número da conta — caminho usado pelas consultas REST. */
    public ContaBancaria recuperarConta(String numeroConta) {
        if (numeroConta == null) {
            return null;
        }
        return this.contas.get(numeroConta);
    }

    public boolean existeConta(String numeroConta) {
        return numeroConta != null && this.contas.containsKey(numeroConta);
    }

    // --- Suporte a snapshot (persistência do estado da StateMachine em disco) ---

    /** Cópia do conteúdo atual, para gravar num snapshot. */
    public Map<String, ContaBancaria> snapshot() {
        return new HashMap<>(this.contas);
    }

    /** Substitui todo o conteúdo pelo que foi carregado de um snapshot. */
    public void restaurar(Map<String, ContaBancaria> dados) {
        this.contas.clear();
        if (dados != null) {
            this.contas.putAll(dados);
        }
    }
}
