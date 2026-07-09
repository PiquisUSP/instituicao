package pubsub;

import java.rmi.Remote;
import java.rmi.RemoteException;

// Serviço do Banco Central onde as instituições se anunciam. Depois de registrada,
// o BC se inscreve de volta no serviço de descoberta da instituição.
public interface RegistroInstituicaoInterface extends Remote {

    void registrar(String idInstituicao, String host, int porta) throws RemoteException;
}
