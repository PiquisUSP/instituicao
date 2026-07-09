package instituicao.seguranca;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// Sessões de login em memória, por nó (não replicadas). O login gera um token opaco
// ligado à conta; saldo/extrato apresentam esse token. Como é por nó, o cliente deve
// seguir no mesmo nó em que logou (os dados das contas, esses sim, são replicados).
@Component
public class SessaoService {

    private final Map<String, String> tokenParaConta = new ConcurrentHashMap<>();

    public String abrir(String numeroConta) {
        String token = UUID.randomUUID().toString();
        tokenParaConta.put(token, numeroConta);
        return token;
    }

    public String contaDoToken(String token) {
        return token == null ? null : tokenParaConta.get(token);
    }

    public boolean autorizado(String token, String numeroConta) {
        return numeroConta != null && numeroConta.equals(contaDoToken(token));
    }

    // Igual ao autorizado, extraindo o token do header Authorization.
    public boolean autorizadoHeader(String authorization, String numeroConta) {
        return autorizado(extrairToken(authorization), numeroConta);
    }

    // Tira o token de "Authorization: Bearer <token>" (ou aceita o valor cru).
    public static String extrairToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String v = authorization.trim();
        return v.regionMatches(true, 0, "Bearer ", 0, 7) ? v.substring(7).trim() : v;
    }
}
