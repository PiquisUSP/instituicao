package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Contrato RMI de <b>registro de chaves</b> do {@code servidor-de-chaves}.
 *
 * <p>Cópia do contrato publicado por aquele projeto — o cliente RMI precisa da
 * mesma interface (mesmo nome e assinaturas) para o stub funcionar. Só usa tipos
 * primitivos (String/int), então não há DTO compartilhado a manter.
 */
public interface RegistroChaveInterface extends Remote {

    int registrarChaveCPF(String idInstituicao, String numeroConta, String cpf) throws RemoteException;

    int registrarChaveTelefone(String idInstituicao, String numeroConta, String telefone) throws RemoteException;

    int registrarChaveEmail(String idInstituicao, String numeroConta, String email) throws RemoteException;

    int registrarChaveAleatoria(String idInstituicao, String numeroConta) throws RemoteException;
}
