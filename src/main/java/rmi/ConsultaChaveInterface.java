package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

import rmi.services.result.ServiceResult;

/**
 * Contrato RMI de <b>consulta de chaves</b> do {@code servidor-de-chaves}.
 *
 * <p>O cliente desta instituição usa apenas {@link #existeChave(String)} (boolean).
 * {@link #consultarChave(String)} faz parte do contrato mas não é chamado aqui:
 * ele retornaria um {@code ContaBancariaResult} contendo o {@code ContaBancaria}
 * do outro projeto — classe de forma diferente da nossa, logo incompatível para
 * desserializar.
 */
public interface ConsultaChaveInterface extends Remote {

    ServiceResult consultarChave(String valor) throws RemoteException;

    boolean existeChave(String valor) throws RemoteException;
}
