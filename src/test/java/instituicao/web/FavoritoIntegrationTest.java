package instituicao.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
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
 * Testes ponta a ponta dos favoritos de transferência (destinos salvos com apelido).
 * Roda em modo local — o favorito é gravado no banco replicado via aplicador e lido
 * de volta pelo GET. Cada teste usa uma conta distinta (banco é singleton do contexto).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"instituicao.raft.enabled=false", "descoberta.enabled=false"})
class FavoritoIntegrationTest {

    private static final String CPF_VALIDO = "529.982.247-25";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {
            };

    @Autowired
    private TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void salvarEListarFavorito_autenticado() {
        criar("7001-1");
        String token = login("7001-1", "segredo123");

        Map<String, String> fav = Map.of(
                "apelido", "Maria",
                "idInstituicao", "INST-0002",
                "numeroConta", "9090-9",
                "nome", "Maria Silva");
        ResponseEntity<Map<String, Object>> criado = post("/contas/7001-1/favoritos", fav, token);

        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(criado.getBody()).containsEntry("apelido", "Maria");
        assertThat(criado.getBody()).containsEntry("numeroConta", "9090-9");
        assertThat(criado.getBody().get("id")).asString().isNotBlank();

        ResponseEntity<Map<String, Object>> lista = get("/contas/7001-1/favoritos", token);
        assertThat(lista.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> favoritos = (List<Map<String, Object>>) lista.getBody().get("favoritos");
        assertThat(favoritos).hasSize(1);
        assertThat(favoritos.get(0)).containsEntry("apelido", "Maria");
        assertThat(favoritos.get(0)).containsEntry("nome", "Maria Silva");
    }

    @Test
    void salvarFavorito_semToken_retorna401() {
        criar("7002-2");
        Map<String, String> fav = Map.of("apelido", "X", "idInstituicao", "INST-0002", "numeroConta", "1-1");
        ResponseEntity<Map<String, Object>> r = post("/contas/7002-2/favoritos", fav, null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void salvarFavorito_semApelido_retorna400() {
        criar("7003-3");
        String token = login("7003-3", "segredo123");
        Map<String, String> fav = Map.of("idInstituicao", "INST-0002", "numeroConta", "1-1");
        ResponseEntity<Map<String, Object>> r = post("/contas/7003-3/favoritos", fav, token);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void salvarFavorito_semDestino_retorna400() {
        criar("7004-4");
        String token = login("7004-4", "segredo123");
        ResponseEntity<Map<String, Object>> r = post("/contas/7004-4/favoritos", Map.of("apelido", "Fulano"), token);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("unchecked")
    void removerFavorito_saiDaLista() {
        criar("7005-5");
        String token = login("7005-5", "segredo123");
        Map<String, String> fav = Map.of("apelido", "João", "idInstituicao", "INST-0002", "numeroConta", "5-5");
        String id = post("/contas/7005-5/favoritos", fav, token).getBody().get("id").toString();

        ResponseEntity<Map<String, Object>> del = exchange(HttpMethod.DELETE, "/contas/7005-5/favoritos/" + id, token);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map<String, Object>> lista = get("/contas/7005-5/favoritos", token);
        assertThat((List<Object>) lista.getBody().get("favoritos")).isEmpty();
    }

    @Test
    void removerFavorito_inexistente_retorna404() {
        criar("7006-6");
        String token = login("7006-6", "segredo123");
        ResponseEntity<Map<String, Object>> r = exchange(HttpMethod.DELETE, "/contas/7006-6/favoritos/nao-existe", token);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- helpers ----------

    private void criar(String numero) {
        Map<String, String> body = new HashMap<>();
        body.put("cpf", CPF_VALIDO);
        body.put("senha", "segredo123");
        body.put("numeroConta", numero);
        body.put("nome", "Fulano de Tal");
        post("/contas", body, null);
    }

    private String login(String numero, String senha) {
        ResponseEntity<Map<String, Object>> r =
                post("/sessoes", Map.of("numeroConta", numero, "senha", senha), null);
        return (String) r.getBody().get("token");
    }

    private ResponseEntity<Map<String, Object>> post(String path, Map<String, String> body, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), MAP);
    }

    private ResponseEntity<Map<String, Object>> get(String path, String token) {
        return exchange(HttpMethod.GET, path, token);
    }

    private ResponseEntity<Map<String, Object>> exchange(HttpMethod metodo, String path, String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        return rest.exchange(path, metodo, new HttpEntity<>(h), MAP);
    }
}
