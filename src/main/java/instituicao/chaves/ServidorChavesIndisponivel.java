package instituicao.chaves;

// O servidor de chaves não pôde ser alcançado via RMI.
public class ServidorChavesIndisponivel extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ServidorChavesIndisponivel(String host, int port, Throwable causa) {
        super("Servidor de chaves indisponível em " + host + ":" + port, causa);
    }
}
