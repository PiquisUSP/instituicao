package estruturas.db;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import estruturas.conta.ContaBancaria;
import estruturas.db.exceptions.conta.ContaJaRegistrada;
import estruturas.transacao.TransacaoPendente;

public class BancoDeDados {

    private static final Logger LOG = LoggerFactory.getLogger(BancoDeDados.class);

    private final ConcurrentHashMap<String, ContaBancaria> contas = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TransacaoPendente> pendentes = new ConcurrentHashMap<>();

    public String id;

    public BancoDeDados(String idInstituicao) {
        this.id = idInstituicao;
        this.carregarDados();
    }

    private void carregarDados() {
    }

    public void adicionarConta(ContaBancaria conta) throws ContaJaRegistrada {
        String numero = conta.getNumeroConta().getValor();
        if (this.contas.containsKey(numero)) {
            throw new ContaJaRegistrada();
        }
        this.contas.put(numero, conta);
        LOG.info("conta salva: {}", numero);
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

    public void registrarPendente(TransacaoPendente pendente) {
        this.pendentes.put(pendente.getId(), pendente);
        LOG.info("transação pendente registrada: {}", pendente);
    }

    public TransacaoPendente recuperarPendente(UUID id) {
        return id == null ? null : this.pendentes.get(id);
    }

    public void removerTransacaoPendente(TransacaoPendente pendente) {
        this.pendentes.remove(pendente.getId());
    }

    public EstadoBanco snapshot() {
        return new EstadoBanco(this.contas, this.pendentes);
    }

    public void restaurar(EstadoBanco estado) {
        this.contas.clear();
        this.pendentes.clear();
        if (estado != null) {
            this.contas.putAll(estado.contas());
            this.pendentes.putAll(estado.pendentes());
        }
    }
}
