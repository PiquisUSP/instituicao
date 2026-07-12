package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface TransacaoInterface extends Remote {

    boolean prepare(UUID idTransacao, String idInstituicaoOrigem, String contaOrigem,
            String idInstituicaoDestino, String contaDestino, long valorCentavos) throws RemoteException;

    boolean commit(UUID idTransacao) throws RemoteException;

    boolean cancel(UUID idTransacao) throws RemoteException;
}
