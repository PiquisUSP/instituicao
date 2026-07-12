package instituicao.pubsub;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pubsub.AssinanteLiderInterface;
import pubsub.DescobertaLiderInterface;
import pubsub.EventoLider;

public class PublicadorLiderService extends UnicastRemoteObject implements DescobertaLiderInterface {

    private static final Logger log = LoggerFactory.getLogger(PublicadorLiderService.class);

    private final Set<AssinanteLiderInterface> assinantes = new CopyOnWriteArraySet<>();
    private volatile EventoLider atual;

    public PublicadorLiderService(int portaExport) throws RemoteException {
        super(portaExport);
    }

    @Override
    public EventoLider assinar(AssinanteLiderInterface assinante) throws RemoteException {
        assinantes.add(assinante);
        log.info("[PUBSUB] novo assinante (Banco Central), total={}", assinantes.size());
        return atual;
    }

    @Override
    public void desassinar(AssinanteLiderInterface assinante) throws RemoteException {
        assinantes.remove(assinante);
    }

    public void publicar(EventoLider evento) {
        this.atual = evento;
        for (AssinanteLiderInterface a : assinantes) {
            try {
                a.onLiderAtualizado(evento);
            } catch (RemoteException e) {
                log.warn("[PUBSUB] assinante inacessível, removendo");
                assinantes.remove(a);
            }
        }
        log.info("[PUBSUB] publicado a {} assinante(s)", assinantes.size());
    }
}
