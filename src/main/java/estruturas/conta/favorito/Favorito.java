package estruturas.conta.favorito;

import java.io.Serializable;

// Um destino salvo pelo dono da conta para transferir mais rápido, com um apelido
// escolhido por ele (estilo Inter). Guarda o destino já resolvido (instituição, conta
// e titular) e, quando salvo por chave, a própria chave — usada para re-resolver na
// hora de transferir (cobre portabilidade da chave).
public class Favorito implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String apelido;
    private String chave; // opcional: preenchido quando o favorito foi salvo por chave
    private String idInstituicao;
    private String numeroConta;
    private String nomeTitular;

    public Favorito(String id, String apelido, String chave,
                    String idInstituicao, String numeroConta, String nomeTitular) {
        this.id = id;
        this.apelido = apelido;
        this.chave = chave;
        this.idInstituicao = idInstituicao;
        this.numeroConta = numeroConta;
        this.nomeTitular = nomeTitular;
    }

    public String getId() {
        return id;
    }

    public String getApelido() {
        return apelido;
    }

    public String getChave() {
        return chave;
    }

    public String getIdInstituicao() {
        return idInstituicao;
    }

    public String getNumeroConta() {
        return numeroConta;
    }

    public String getNomeTitular() {
        return nomeTitular;
    }
}
