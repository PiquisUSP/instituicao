package raft;

import java.io.Serializable;

import estruturas.db.BancoDeDados;

// Um comando que viaja no log do Raft e é reaplicado por cada nó. A operação precisa
// ser determinística: os mesmos dados produzem o mesmo efeito em todos os nós — por
// isso valores aleatórios (número da conta, id do favorito) são resolvidos no
// controller, antes de o comando entrar no log.
public interface Comando extends Serializable {

    // Aplica a mutação no banco e devolve o status: 200 (ok), 403 (conflito),
    // 404 (alvo ausente). Exceções inesperadas viram 500 no chamador.
    int aplicar(BancoDeDados db);
}
