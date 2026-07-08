package instituicao.chaves;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import rmi.ConsultaChaveInterface;
import rmi.RegistroChaveInterface;

/**
 * Cliente RMI do {@code servidor-de-chaves} (o "PIX"): registra e consulta chaves.
 *
 * <p>Faz o lookup no registry a cada chamada (barato) — assim tolera reinícios do
 * servidor sem guardar stubs velhos. Falhas de conectividade viram
 * {@link ServidorChavesIndisponivel} para o controller mapear a um 502.
 *
 * <p>Endereço configurável por {@code chaves.host}/{@code chaves.port}
 * (env {@code CHAVES_HOST}/{@code CHAVES_PORT}).
 */
@Component
public class ClienteServidorChaves {

    private final String host;
    private final int port;

    public ClienteServidorChaves(
            @Value("${chaves.host:127.0.0.1}") String host,
            @Value("${chaves.port:1099}") int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Registra uma chave no servidor de chaves.
     *
     * @return status do servidor: 200 (registrada), 403 (já existe), 500 (recusada).
     * @throws ServidorChavesIndisponivel se não conseguir falar com o servidor.
     */
    public int registrar(TipoChave tipo, String idInstituicao, String numeroConta, String valor) {
        try {
            RegistroChaveInterface registro = (RegistroChaveInterface) registry().lookup("RegistroChave");
            return switch (tipo) {
                case CPF -> registro.registrarChaveCPF(idInstituicao, numeroConta, valor);
                case TELEFONE -> registro.registrarChaveTelefone(idInstituicao, numeroConta, valor);
                case EMAIL -> registro.registrarChaveEmail(idInstituicao, numeroConta, valor);
                case ALEATORIA -> registro.registrarChaveAleatoria(idInstituicao, numeroConta);
            };
        } catch (Exception e) {
            throw new ServidorChavesIndisponivel(host, port, e);
        }
    }

    /** true se a chave já existe no servidor de chaves. */
    public boolean existe(String valor) {
        try {
            ConsultaChaveInterface consulta = (ConsultaChaveInterface) registry().lookup("ConsultaChave");
            return consulta.existeChave(valor);
        } catch (Exception e) {
            throw new ServidorChavesIndisponivel(host, port, e);
        }
    }

    private Registry registry() throws java.rmi.RemoteException {
        return LocateRegistry.getRegistry(host, port);
    }
}
