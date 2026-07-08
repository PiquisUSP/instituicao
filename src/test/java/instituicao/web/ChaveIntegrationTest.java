package instituicao.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Testes da ponte com o servidor de chaves. O servidor de chaves NÃO está no ar
 * (apontamos {@code chaves.port} para uma porta morta), então validamos o
 * roteamento, a autenticação e o mapeamento de falha para 502 — sem depender de
 * um cluster de chaves rodando.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "instituicao.raft.enabled=false",
                "chaves.host=127.0.0.1",
                "chaves.port=19099" // nada escutando aqui -> falha rápida
        })
class ChaveIntegrationTest {

    private static final String CPF_VALIDO = "529.982.247-25";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {
            };

    @Autowired
    private TestRestTemplate rest;

    @Test
    void registrarChave_semToken_retorna401() {
        criar("5001-1");
        ResponseEntity<Map<String, Object>> r =
                post("/contas/5001-1/chaves", Map.of("tipo", "CPF", "valor", CPF_VALIDO), null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registrarChave_tipoInvalido_retorna400() {
        criar("5002-2");
        String token = login("5002-2");
        ResponseEntity<Map<String, Object>> r =
                post("/contas/5002-2/chaves", Map.of("tipo", "INVALIDO", "valor", "x"), token);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registrarChave_cpfSemValor_retorna400() {
        criar("5003-3");
        String token = login("5003-3");
        ResponseEntity<Map<String, Object>> r =
                post("/contas/5003-3/chaves", Map.of("tipo", "CPF"), token);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registrarChave_comServidorDeChavesForaDoAr_retorna502() {
        criar("5004-4");
        String token = login("5004-4");
        ResponseEntity<Map<String, Object>> r =
                post("/contas/5004-4/chaves", Map.of("tipo", "CPF", "valor", CPF_VALIDO), token);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void existeChave_comServidorDeChavesForaDoAr_retorna502() {
        ResponseEntity<Map<String, Object>> r = get("/chaves/qualquer/existe", null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    // ---------- helpers ----------

    private void criar(String numero) {
        Map<String, String> body = new HashMap<>();
        body.put("cpf", CPF_VALIDO);
        body.put("senha", "segredo123");
        body.put("numeroConta", numero);
        post("/contas", body, null);
    }

    private String login(String numero) {
        ResponseEntity<Map<String, Object>> r =
                post("/sessoes", Map.of("numeroConta", numero, "senha", "segredo123"), null);
        return (String) r.getBody().get("token");
    }

    private ResponseEntity<Map<String, Object>> post(String path, Map<String, ?> body, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), MAP);
    }

    private ResponseEntity<Map<String, Object>> get(String path, String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), MAP);
    }
}
