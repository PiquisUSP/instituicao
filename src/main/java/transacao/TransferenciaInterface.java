package transacao;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface TransferenciaInterface extends Remote {
    boolean solicitaTransacao(String idInstituicaoOrigem, String contaOrigem, String idInstituicaoDestino, String contaDestino, long valorCentavos) throws RemoteException;
}
