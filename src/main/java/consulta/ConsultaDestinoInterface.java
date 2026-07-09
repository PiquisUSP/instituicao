package consulta;

import java.rmi.Remote;
import java.rmi.RemoteException;

// Exposto pelo Banco Central: dado instituição + conta, ele roteia para a instituição
// de destino (pela tabela de roteamento) e devolve o titular.
public interface ConsultaDestinoInterface extends Remote {

    RespostaConta consultarConta(String idInstituicao, String numeroConta) throws RemoteException;
}
