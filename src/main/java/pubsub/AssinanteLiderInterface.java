package pubsub;

import java.rmi.Remote;
import java.rmi.RemoteException;

// Callback do assinante (Banco Central). A instituição chama isto quando troca de líder.
public interface AssinanteLiderInterface extends Remote {

    void onLiderAtualizado(EventoLider evento) throws RemoteException;
}
