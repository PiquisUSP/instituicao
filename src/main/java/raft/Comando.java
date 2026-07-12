package raft;

import java.io.Serializable;

import estruturas.db.BancoDeDados;

public interface Comando extends Serializable {

    int aplicar(BancoDeDados db);
}
