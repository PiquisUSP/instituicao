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

// Modo replicado (padrão): sobe o nó Raft e expõe o aplicador (escritas via consenso)
// e o banco replicado (leituras) como beans. Ativo quando instituicao.raft.enabled é
// true ou ausente.
@Configuration
@ConditionalOnProperty(name = "instituicao.raft.enabled", havingValue = "true", matchIfMissing = true)
public class RaftModeConfig {

    private static final Logger log = LoggerFactory.getLogger(RaftModeConfig.class);

    @Bean(destroyMethod = "close") // fechado no shutdown do Spring
    public NoInstituicao noInstituicao(@Value("${instituicao.node-id:n1}") String nodeId) throws IOException {
        log.info("[CONFIG] modo RAFT (replicado) — nó={}; iniciando consenso...", nodeId);
        NoInstituicao no = new NoInstituicao(nodeId);
        no.iniciar();
        log.info("[CONFIG] nó Raft '{}' iniciado (aguarda eleição de líder com maioria dos nós)", nodeId);
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
