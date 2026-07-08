package instituicao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada de um nó do servidor de instituição.
 *
 * <p>Sobe duas camadas: <b>REST</b> (Tomcat embutido do Spring, na
 * {@code server.port}) para os clientes, e <b>Raft/gRPC</b> (consenso entre os
 * nós) inicializado pela configuração {@code RaftModeConfig}.
 *
 * <p>Rodar um nó (ex.: n2):
 * {@code mvnw spring-boot:run -Dspring-boot.run.profiles=n2}
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
