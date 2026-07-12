package instituicao.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import estruturas.db.BancoDeDados;
import raft.AplicadorDeContas;
import raft.AplicadorLocal;

@Configuration
@ConditionalOnProperty(name = "instituicao.raft.enabled", havingValue = "false")
public class LocalModeConfig {

    private static final Logger log = LoggerFactory.getLogger(LocalModeConfig.class);

    @Bean
    public BancoDeDados banco() {
        log.info("[CONFIG] modo LOCAL");
        return new BancoDeDados("INST-001");
    }

    @Bean
    public AplicadorDeContas aplicador(BancoDeDados banco) {
        return new AplicadorLocal(banco);
    }
}
