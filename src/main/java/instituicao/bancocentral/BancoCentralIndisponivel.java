package instituicao.bancocentral;

public class BancoCentralIndisponivel extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BancoCentralIndisponivel(String host, int porta, Throwable causa) {
        super("Banco Central indisponível em " + host + ":" + porta, causa);
    }
}
