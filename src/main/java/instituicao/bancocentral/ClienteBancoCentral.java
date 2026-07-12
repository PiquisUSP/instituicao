package instituicao.bancocentral;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import consulta.ConsultaDestinoInterface;
import consulta.RespostaConta;
import transacao.TransferenciaInterface;

// Cliente RMI do Banco Central. Resolve o destino de uma transferência: o BC roteia
// a consulta para a instituição de destino e devolve o titular.
@Component
public class ClienteBancoCentral {

    private static final Logger log = LoggerFactory.getLogger(ClienteBancoCentral.class);

    private final String host;
    private final int porta;

    public ClienteBancoCentral(
            @Value("${bc.host:127.0.0.1}") String host,
            @Value("${bc.port:1200}") int porta) {
        this.host = host;
        this.porta = porta;
    }

    public RespostaConta consultarConta(String idInstituicao, String numeroConta) {
        log.info("[BC] -> {}:{} consultarConta({}, {})", host, porta, idInstituicao, numeroConta);
        try {
            Registry reg = LocateRegistry.getRegistry(host, porta);
            ConsultaDestinoInterface bc = (ConsultaDestinoInterface) reg.lookup("ConsultaDestino");
            RespostaConta r = bc.consultarConta(idInstituicao, numeroConta);
            log.info("[BC] <- existe={}, nome={}", r.existe, r.nome);
            return r;
        } catch (Exception e) {
            log.error("[BC] falha ao falar com o Banco Central {}:{} ({})", host, porta, e.toString());
            throw new BancoCentralIndisponivel(host, porta, e);
        }
    }

    public boolean solicitaTransacao(String idInstituicaoOrigem, String contaOrigem, String idInstituicaoDestino, String contaDestino, long valorCentavos) {
        try {
            Registry reg = LocateRegistry.getRegistry(host, porta);
            TransferenciaInterface bc = (TransferenciaInterface) reg.lookup("Transferencia");
            return bc.solicitaTransacao(idInstituicaoOrigem, contaOrigem, idInstituicaoDestino, contaDestino, valorCentavos);
        } catch (Exception e) {
            log.error("[BC] falha ao falar com o Banco Central {}:{} ({})", host, porta, e.toString());
            throw new BancoCentralIndisponivel(host, porta, e);
        }
    }
}
