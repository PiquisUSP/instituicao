package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface TransacaoInterface extends Remote {

    // PREPARE (fase 1 do 2PC). A instituição reserva/valida a conta que é dela e responde
    // o voto: true = pronta para o COMMIT, false = não dá para prosseguir.
    boolean prepare(UUID idTransacao, String idInstituicaoOrigem, String contaOrigem,
            String idInstituicaoDestino, String contaDestino, long valorCentavos) throws RemoteException;

    boolean commit(UUID idTransacao) throws RemoteException;

    // ABORT (fase 2, caminho de cancelamento). Libera a reserva e descarta a pendência.
    boolean cancel(UUID idTransacao) throws RemoteException;
}
