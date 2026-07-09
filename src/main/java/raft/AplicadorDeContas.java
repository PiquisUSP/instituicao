package raft;

// Abstrai como uma criação de conta é efetivada: direto no banco (AplicadorLocal)
// ou replicada via Raft (AplicadorRaft). O controller não precisa saber qual é.
public interface AplicadorDeContas {

    // 200 = criou, 403 = já existia, 500 = falha.
    int registrar(ComandoCriarConta comando);
}
