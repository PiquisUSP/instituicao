package instituicao.pubsub;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import pubsub.EventoLider;
import pubsub.RegistroInstituicaoInterface;
import raft.NoInstituicao;

// Descoberta de líder, lado da instituição:
//  - sobe um registry RMI (descoberta.port) com o PublicadorLiderService;
//  - registra-se no Banco Central (modelo híbrido) e reenvia periodicamente (idempotente);
//  - publica um evento quando este nó vira líder. Em modo local o nó é sempre líder.
// Desligável com descoberta.enabled=false (usado nos testes).
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "descoberta.enabled", havingValue = "true", matchIfMissing = true)
public class DescobertaLiderConfig {

    private static final Logger log = LoggerFactory.getLogger(DescobertaLiderConfig.class);
    private static final String NOME_DESCOBERTA = "DescobertaLider";
    private static final String NOME_REGISTRO = "RegistroInstituicao";

    private final String idInstituicao;
    private final int porta;
    private final String descobertaHost;
    private final String enderecoPublico;
    private final String bcHost;
    private final int bcPorta;
    private final ObjectProvider<NoInstituicao> noProvider;

    private PublicadorLiderService publicador;
    private Registry registry;
    private boolean eraLider = false;
    private long termo = 0;
    private volatile boolean registradoNoBc = false;

    public DescobertaLiderConfig(
            @Value("${instituicao.id:INST-0001}") String idInstituicao,
            @Value("${descoberta.port:9001}") int porta,
            @Value("${descoberta.host:127.0.0.1}") String descobertaHost,
            @Value("${descoberta.endereco-publico:}") String enderecoPublico,
            @Value("${bc.host:127.0.0.1}") String bcHost,
            @Value("${bc.port:1200}") int bcPorta,
            ObjectProvider<NoInstituicao> noProvider) {
        this.idInstituicao = idInstituicao;
        this.porta = porta;
        this.descobertaHost = descobertaHost;
        this.enderecoPublico = enderecoPublico.isBlank() ? descobertaHost + ":" + porta : enderecoPublico;
        this.bcHost = bcHost;
        this.bcPorta = bcPorta;
        this.noProvider = noProvider;
    }

    @PostConstruct
    void iniciar() throws RemoteException {
        publicador = new PublicadorLiderService(porta);
        registry = LocateRegistry.createRegistry(porta);
        registry.rebind(NOME_DESCOBERTA, publicador);
        log.info("[PUBSUB] serviço '{}' publicado na porta RMI {} (instituicao {}, endereço público {})",
                NOME_DESCOBERTA, porta, idInstituicao, enderecoPublico);
    }

    // Registra no BC; retenta sempre (idempotente), então sobrevive a reinício do BC.
    @Scheduled(fixedDelay = 3000)
    void registrarNoBancoCentral() {
        try {
            Registry bcRegistry = LocateRegistry.getRegistry(bcHost, bcPorta);
            RegistroInstituicaoInterface registro =
                    (RegistroInstituicaoInterface) bcRegistry.lookup(NOME_REGISTRO);
            registro.registrar(idInstituicao, descobertaHost, porta);
            if (!registradoNoBc) {
                log.info("[REGISTRO] registrado no Banco Central {}:{} como {} ({}:{})",
                        bcHost, bcPorta, idInstituicao, descobertaHost, porta);
                registradoNoBc = true;
            }
        } catch (Exception e) {
            if (registradoNoBc) {
                log.warn("[REGISTRO] contato com o Banco Central perdido ({}); re-registrando...",
                        e.getClass().getSimpleName());
                registradoNoBc = false;
            }
            // BC ainda não no ar: silencioso até subir.
        }
    }

    // Observa a liderança e publica quando este nó vira líder.
    @Scheduled(fixedDelay = 2000)
    void verificarLideranca() {
        boolean lider = ehLider();
        if (lider && !eraLider) {
            termo++;
            EventoLider evento = new EventoLider(idInstituicao, enderecoPublico, termo);
            log.info("[PUBSUB] este nó tornou-se LÍDER — publicando {}", evento);
            publicador.publicar(evento);
        } else if (!lider && eraLider) {
            log.info("[PUBSUB] este nó deixou de ser líder");
        }
        eraLider = lider;
    }

    // Modo Raft: usa isLider(); modo local (sem o bean NoInstituicao): sempre líder.
    private boolean ehLider() {
        NoInstituicao no = noProvider.getIfAvailable();
        return no == null || no.isLider();
    }

    @PreDestroy
    void encerrar() {
        try {
            if (registry != null) {
                registry.unbind(NOME_DESCOBERTA);
            }
        } catch (Exception ignored) {
            // encerrando
        }
        try {
            if (publicador != null) {
                UnicastRemoteObject.unexportObject(publicador, true);
            }
        } catch (Exception ignored) {
            // encerrando
        }
    }
}
