package rmi.services.result;

import java.io.Serializable;

/**
 * Resultado base retornado por RMI pelo {@code servidor-de-chaves}. Cópia com o
 * mesmo {@code serialVersionUID} para o marshalling ser compatível.
 *
 * <p>Necessário apenas porque a {@link rmi.ConsultaChaveInterface} o declara no
 * tipo de retorno de {@code consultarChave}. O cliente aqui usa somente
 * {@code existeChave} (boolean); {@code consultarChave} não é exposto porque
 * devolveria um {@code ContaBancaria} no formato do outro projeto, incompatível
 * com o desta instituição.
 */
public class ServiceResult implements Serializable {
    private static final long serialVersionUID = 1L;

    public int statusCode;

    public ServiceResult(int statusCode) {
        this.statusCode = statusCode;
    }
}
