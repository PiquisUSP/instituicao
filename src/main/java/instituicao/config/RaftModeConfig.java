package instituicao.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import estruturas.db.BancoDeDados;
import raft.AplicadorDeContas;
import raft.NoInstituicao;

@Configuration
@ConditionalOnProperty(name = "instituicao.raft.enabled", havingValue = "true", matchIfMissing = true)
public class RaftModeConfig {

    private static final Logger log = LoggerFactory.getLogger(RaftModeConfig.class);

    @Bean(destroyMethod = "close")
    public NoInstituicao noInstituicao(@Value("${instituicao.node-id:n1}") String nodeId) throws IOException {
        log.info("[CONFIG] modo RAFT, nó={}", nodeId);
        NoInstituicao no = new NoInstituicao(nodeId);
        no.iniciar();
        return no;
    }

    @Bean
    public AplicadorDeContas aplicador(NoInstituicao no) {
        return no.aplicador();
    }

    @Bean
    public BancoDeDados banco(NoInstituicao no) {
        return no.banco();
    }
}
