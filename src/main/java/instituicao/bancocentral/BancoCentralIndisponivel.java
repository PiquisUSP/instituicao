package instituicao.bancocentral;

// O Banco Central não pôde ser alcançado via RMI.
public class BancoCentralIndisponivel extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BancoCentralIndisponivel(String host, int porta, Throwable causa) {
        super("Banco Central indisponível em " + host + ":" + porta, causa);
    }
}
