package raft;

// Abstrai como um comando é efetivado: direto no banco (AplicadorLocal) ou replicado
// via Raft (AplicadorRaft). O controller não precisa saber qual é.
public interface AplicadorDeContas {

    // 200 = aplicado, 403 = conflito, 404 = alvo ausente, 500 = falha.
    int registrar(Comando comando);
}
