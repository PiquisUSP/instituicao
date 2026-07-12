package instituicao.consulta;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import consulta.ConsultaContaInterface;
import consulta.RespostaConta;
import estruturas.conta.ContaBancaria;
import estruturas.db.BancoDeDados;

public class ConsultaContaService extends UnicastRemoteObject implements ConsultaContaInterface {

    private static final Logger LOG = LoggerFactory.getLogger(ConsultaContaService.class);

    private final BancoDeDados banco;

    public ConsultaContaService(BancoDeDados banco, int portaExport) throws RemoteException {
        super(portaExport);
        this.banco = banco;
    }

    @Override
    public RespostaConta consultarConta(String numeroConta) throws RemoteException {
        ContaBancaria c = banco.recuperarConta(numeroConta);
        LOG.info("[CONSULTA] conta {} existe={}", numeroConta, c != null);
        return c == null ? RespostaConta.naoExiste() : RespostaConta.de(c.getNome());
    }
}
