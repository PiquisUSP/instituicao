package rmi.services.result;

import java.io.Serializable;

public class ServiceResult implements Serializable {
    private static final long serialVersionUID = 1L;

    public int statusCode;

    public ServiceResult(int statusCode) {
        this.statusCode = statusCode;
    }
}
