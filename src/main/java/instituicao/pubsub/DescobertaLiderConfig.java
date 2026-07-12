package instituicao.pubsub;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

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
import estruturas.db.BancoDeDados;
import instituicao.consulta.ConsultaContaService;
import pubsub.EventoLider;
import pubsub.RegistroInstituicaoInterface;
import raft.AplicadorDeContas;
import raft.NoInstituicao;
import rmi.services.TransacaoService;

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
    private final List<String> bcHosts;
    private final int bcPorta;
    private final ObjectProvider<NoInstituicao> noProvider;
    private final BancoDeDados banco;
    private final AplicadorDeContas aplicador;

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
            @Value("${bc.hosts:${bc.host:127.0.0.1}}") String bcHostsCsv,
            @Value("${bc.port:1200}") int bcPorta,
            ObjectProvider<NoInstituicao> noProvider,
            BancoDeDados banco,
            AplicadorDeContas aplicador) {
        this.idInstituicao = idInstituicao;
        this.porta = porta;
        this.descobertaHost = descobertaHost;
        this.enderecoPublico = enderecoPublico.isBlank() ? descobertaHost + ":" + porta : enderecoPublico;
        this.bcHosts = parseHosts(bcHostsCsv);
        this.bcPorta = bcPorta;
        this.noProvider = noProvider;
        this.banco = banco;
        this.aplicador = aplicador;
    }

    private static List<String> parseHosts(String csv) {
        List<String> out = new ArrayList<>();
        if (csv != null) {
            for (String s : csv.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
        }
        if (out.isEmpty()) {
            out.add("127.0.0.1");
        }
        return out;
    }

    @PostConstruct
    void iniciar() throws RemoteException {
        publicador = new PublicadorLiderService(porta);
        registry = LocateRegistry.createRegistry(porta);
        registry.rebind(NOME_DESCOBERTA, publicador);
        registry.rebind("ConsultaConta", new ConsultaContaService(banco, porta));
        registry.rebind("Transacao", new TransacaoService(aplicador, banco, idInstituicao, porta));
        log.info("[PUBSUB] serviços publicados na porta {} (instituicao {})", porta, idInstituicao);
    }

    @Scheduled(fixedDelay = 3000)
    void registrarNoBancoCentral() {
        int ok = 0;
        for (String bcHost : bcHosts) {
            try {
                Registry bcRegistry = LocateRegistry.getRegistry(bcHost, bcPorta);
                RegistroInstituicaoInterface registro =
                        (RegistroInstituicaoInterface) bcRegistry.lookup(NOME_REGISTRO);
                registro.registrar(idInstituicao, descobertaHost, porta);
                ok++;
            } catch (Exception e) {
            }
        }
        if (ok > 0 && !registradoNoBc) {
            log.info("[REGISTRO] registrado no Banco Central como {}", idInstituicao);
            registradoNoBc = true;
        } else if (ok == 0 && registradoNoBc) {
            log.warn("[REGISTRO] contato com o Banco Central perdido, re-registrando");
            registradoNoBc = false;
        }
    }

    @Scheduled(fixedDelay = 2000)
    void verificarLideranca() {
        boolean lider = ehLider();
        if (lider && !eraLider) {
            termo++;
            EventoLider evento = new EventoLider(idInstituicao, enderecoPublico, termo);
            log.info("[PUBSUB] este nó virou líder, publicando {}", evento);
            publicador.publicar(evento);
        } else if (!lider && eraLider) {
            log.info("[PUBSUB] este nó deixou de ser líder");
        }
        eraLider = lider;
    }

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
        }
        try {
            if (publicador != null) {
                UnicastRemoteObject.unexportObject(publicador, true);
            }
        } catch (Exception ignored) {
        }
    }
}
