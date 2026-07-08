package instituicao.seguranca;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Sessões de login, guardadas em memória <b>por nó</b> (não são replicadas via
 * Raft). Um login gera um token opaco associado ao número da conta; as consultas
 * autenticadas (saldo/extrato) apresentam esse token.
 *
 * <p>Como as sessões são locais, o cliente deve seguir falando com o mesmo nó em
 * que fez login (sticky). Os <i>dados</i> das contas, esses sim, são replicados.
 */
@Component
public class SessaoService {

    private final Map<String, String> tokenParaConta = new ConcurrentHashMap<>();

    /** Abre uma sessão para a conta e devolve o token. */
    public String abrir(String numeroConta) {
        String token = UUID.randomUUID().toString();
        tokenParaConta.put(token, numeroConta);
        return token;
    }

    /** Número da conta dona do token, ou {@code null} se o token não existe. */
    public String contaDoToken(String token) {
        return token == null ? null : tokenParaConta.get(token);
    }

    /** true se o token é válido e pertence exatamente à conta informada. */
    public boolean autorizado(String token, String numeroConta) {
        return numeroConta != null && numeroConta.equals(contaDoToken(token));
    }

    /** Igual a {@link #autorizado}, extraindo o token de um header Authorization. */
    public boolean autorizadoHeader(String authorization, String numeroConta) {
        return autorizado(extrairToken(authorization), numeroConta);
    }

    /** Extrai o token de um header {@code Authorization: Bearer <token>} (ou o valor cru). */
    public static String extrairToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String v = authorization.trim();
        return v.regionMatches(true, 0, "Bearer ", 0, 7) ? v.substring(7).trim() : v;
    }
}
