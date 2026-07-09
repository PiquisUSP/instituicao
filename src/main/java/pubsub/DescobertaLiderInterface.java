package pubsub;

import java.rmi.Remote;
import java.rmi.RemoteException;

// Serviço que cada instituição publica; o Banco Central se inscreve para saber quem
// é o líder e receber as trocas.
public interface DescobertaLiderInterface extends Remote {

    // Inscreve o assinante e devolve o líder atual (null se ainda não houver).
    EventoLider assinar(AssinanteLiderInterface assinante) throws RemoteException;

    void desassinar(AssinanteLiderInterface assinante) throws RemoteException;
}
