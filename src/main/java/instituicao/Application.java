package instituicao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Entrada de um nó do servidor de instituição: REST (Tomcat do Spring) + Raft/gRPC
// (consenso, ligado pelo RaftModeConfig).
// Rodar um nó (ex.: n2): mvnw spring-boot:run -Dspring-boot.run.profiles=n2
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
