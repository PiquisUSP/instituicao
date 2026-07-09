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
 * Testes de integração ponta a ponta da API (HTTP real + stack Spring + banco
 * local). Roda em modo local ({@code instituicao.raft.enabled=false}) para não
 * depender de um cluster Raft com maioria de nós.
 *
 * <p>O banco é um singleton do contexto, compartilhado entre os testes — por isso
 * cada teste usa números de conta distintos para não interferir nos demais.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"instituicao.raft.enabled=false", "descoberta.enabled=false"})
class ContaIntegrationTest {

    private static final String CPF_VALIDO = "529.982.247-25";
    private static final String CPF_INVALIDO = "111.111.111-11";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {
            };

    @Autowired
    private TestRestTemplate rest;

    // ---------- Criação de conta ----------

    @Test
    void criarConta_comCpfSenhaENumero_retorna201_semVazarSenha() {
        ResponseEntity<Map<String, Object>> r = criar("1001-1", CPF_VALIDO, "segredo123");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody()).containsEntry("numeroConta", "1001-1");
        assertThat(r.getBody()).containsEntry("cpf", CPF_VALIDO);
        assertThat(r.getBody()).containsEntry("nome", "Fulano de Tal");
        // Nunca devolver a senha (nem hash).
        assertThat(r.getBody()).doesNotContainKeys("senha", "senhaHash");
    }

    @Test
    void criarConta_semSenha_retorna400() {
        ResponseEntity<Map<String, Object>> r = criar("1002-2", CPF_VALIDO, null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void criarConta_semNome_retorna400() {
        Map<String, String> body = new HashMap<>();
        body.put("cpf", CPF_VALIDO);
        body.put("senha", "segredo123");
        body.put("numeroConta", "1005-5");
        ResponseEntity<Map<String, Object>> r = post("/contas", body, null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void criarConta_comCpfInvalido_retorna400() {
        ResponseEntity<Map<String, Object>> r = criar("1003-3", CPF_INVALIDO, "segredo123");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void criarConta_comNumeroDuplicado_retorna409() {
        assertThat(criar("1004-4", CPF_VALIDO, "segredo123").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map<String, Object>> segunda = criar("1004-4", CPF_VALIDO, "outra456");
        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void criarConta_semNumero_geraNumeroAutomaticamente() {
        Map<String, String> body = new HashMap<>();
        body.put("cpf", CPF_VALIDO);
        body.put("senha", "segredo123");
        body.put("nome", "Fulano de Tal");
        ResponseEntity<Map<String, Object>> r = post("/contas", body, null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody().get("numeroConta")).asString().isNotBlank();
    }

    // ---------- Login ----------

    @Test
    void login_comSenhaCorreta_retornaToken() {
        criar("2001-1", CPF_VALIDO, "segredo123");

        ResponseEntity<Map<String, Object>> r =
                post("/sessoes", Map.of("numeroConta", "2001-1", "senha", "segredo123"), null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("token")).asString().isNotBlank();
        assertThat(r.getBody()).containsEntry("numeroConta", "2001-1");
    }

    @Test
    void login_comSenhaErrada_retorna401() {
        criar("2002-2", CPF_VALIDO, "segredo123");

        ResponseEntity<Map<String, Object>> r =
                post("/sessoes", Map.of("numeroConta", "2002-2", "senha", "errada"), null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_deContaInexistente_retorna401() {
        ResponseEntity<Map<String, Object>> r =
                post("/sessoes", Map.of("numeroConta", "0000-0", "senha", "seja"), null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------- Saldo ----------

    @Test
    void verSaldo_autenticado_retorna200_comSaldoZero() {
        criar("3001-1", CPF_VALIDO, "segredo123");
        String token = login("3001-1", "segredo123");

        ResponseEntity<Map<String, Object>> r = get("/contas/3001-1/saldo", token);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("numeroConta", "3001-1");
        assertThat(((Number) r.getBody().get("saldoCentavos")).longValue()).isZero();
    }

    @Test
    void verSaldo_semToken_retorna401() {
        criar("3002-2", CPF_VALIDO, "segredo123");
        ResponseEntity<Map<String, Object>> r = get("/contas/3002-2/saldo", null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void verSaldo_comTokenDeOutraConta_retorna401() {
        criar("3003-3", CPF_VALIDO, "segredo123");
        criar("3004-4", CPF_VALIDO, "segredo123");
        String tokenDaOutra = login("3003-3", "segredo123");

        ResponseEntity<Map<String, Object>> r = get("/contas/3004-4/saldo", tokenDaOutra);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------- Extrato ----------

    @Test
    @SuppressWarnings("unchecked")
    void verExtrato_autenticado_retornaListaVazia() {
        criar("4001-1", CPF_VALIDO, "segredo123");
        String token = login("4001-1", "segredo123");

        ResponseEntity<Map<String, Object>> r = get("/contas/4001-1/extrato", token);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("numeroConta", "4001-1");
        assertThat((List<Object>) r.getBody().get("transacoes")).isEmpty();
    }

    @Test
    void verExtrato_semToken_retorna401() {
        criar("4002-2", CPF_VALIDO, "segredo123");
        ResponseEntity<Map<String, Object>> r = get("/contas/4002-2/extrato", null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------- helpers ----------

    private ResponseEntity<Map<String, Object>> criar(String numero, String cpf, String senha) {
        Map<String, String> body = new HashMap<>();
        if (cpf != null) body.put("cpf", cpf);
        if (senha != null) body.put("senha", senha);
        if (numero != null) body.put("numeroConta", numero);
        body.put("nome", "Fulano de Tal");
        return post("/contas", body, null);
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
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), MAP);
    }
}
