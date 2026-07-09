package instituicao.chaves;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import rmi.ConsultaChaveInterface;
import rmi.RegistroChaveInterface;

// Cliente RMI do servidor-de-chaves. Faz o lookup a cada chamada (é barato) para
// tolerar reinícios do servidor. Falha de conexão vira ServidorChavesIndisponivel, que
// o controller mapeia para 502. Endereço em chaves.host/chaves.port.
@Component
public class ClienteServidorChaves {

    private static final Logger log = LoggerFactory.getLogger(ClienteServidorChaves.class);

    private final String host;
    private final int port;

    public ClienteServidorChaves(
            @Value("${chaves.host:127.0.0.1}") String host,
            @Value("${chaves.port:1099}") int port) {
        this.host = host;
        this.port = port;
        log.info("[RMI] cliente do servidor-de-chaves configurado para {}:{}", host, port);
    }

    // Retorna 200 (registrada), 403 (já existe) ou 500 (recusada).
    public int registrar(TipoChave tipo, String idInstituicao, String numeroConta, String valor) {
        log.info("[RMI] -> {}:{} lookup 'RegistroChave'; registrarChave{} (numeroConta={})",
                host, port, tipoSufixo(tipo), numeroConta);
        try {
            RegistroChaveInterface registro = (RegistroChaveInterface) registry().lookup("RegistroChave");
            int status = switch (tipo) {
                case CPF -> registro.registrarChaveCPF(idInstituicao, numeroConta, valor);
                case TELEFONE -> registro.registrarChaveTelefone(idInstituicao, numeroConta, valor);
                case EMAIL -> registro.registrarChaveEmail(idInstituicao, numeroConta, valor);
                case ALEATORIA -> registro.registrarChaveAleatoria(idInstituicao, numeroConta);
            };
            log.info("[RMI] <- resposta do servidor-de-chaves: status={}", status);
            return status;
        } catch (Exception e) {
            log.error("[RMI] falha ao falar com {}:{} ({})", host, port, e.toString());
            throw new ServidorChavesIndisponivel(host, port, e);
        }
    }

    public int atualizar(TipoChave tipo, String idInstituicao, String numeroConta, String valor) {
        log.info("[RMI] -> {}:{} lookup 'RegistroChave'; atualizarChave (tipo={}, numeroConta={})",
                host, port, tipo, numeroConta);
        try {
            RegistroChaveInterface registro = (RegistroChaveInterface) registry().lookup("RegistroChave");
            int status = registro.atualizarChave(tipo.name(), idInstituicao, numeroConta, valor);
            log.info("[RMI] <- resposta do servidor-de-chaves: status={}", status);
            return status;
        } catch (Exception e) {
            log.error("[RMI] falha ao falar com {}:{} ({})", host, port, e.toString());
            throw new ServidorChavesIndisponivel(host, port, e);
        }
    }

    public boolean existe(String valor) {
        log.info("[RMI] -> {}:{} lookup 'ConsultaChave'; existeChave(valor={})", host, port, valor);
        try {
            ConsultaChaveInterface consulta = (ConsultaChaveInterface) registry().lookup("ConsultaChave");
            boolean existe = consulta.existeChave(valor);
            log.info("[RMI] <- existeChave = {}", existe);
            return existe;
        } catch (Exception e) {
            log.error("[RMI] falha ao falar com {}:{} ({})", host, port, e.toString());
            throw new ServidorChavesIndisponivel(host, port, e);
        }
    }

    private Registry registry() throws java.rmi.RemoteException {
        return LocateRegistry.getRegistry(host, port);
    }

    private static String tipoSufixo(TipoChave tipo) {
        return switch (tipo) {
            case CPF -> "CPF";
            case TELEFONE -> "Telefone";
            case EMAIL -> "Email";
            case ALEATORIA -> "Aleatoria";
        };
    }
}
