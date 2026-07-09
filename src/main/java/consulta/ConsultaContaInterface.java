package consulta;

import java.rmi.Remote;
import java.rmi.RemoteException;

// Exposto por cada instituição: dado o número da conta, devolve o titular.
public interface ConsultaContaInterface extends Remote {

    RespostaConta consultarConta(String numeroConta) throws RemoteException;
}
