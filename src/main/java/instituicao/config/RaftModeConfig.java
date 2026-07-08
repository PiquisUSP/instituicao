package instituicao.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import estruturas.db.BancoDeDados;
import raft.AplicadorDeContas;
import raft.NoInstituicao;

/**
 * Modo replicado (padrão): sobe um nó Raft e expõe o aplicador (escritas via
 * consenso) e o banco replicado (leituras locais) como beans.
 *
 * <p>Ativo quando {@code instituicao.raft.enabled} é {@code true} ou ausente.
 */
@Configuration
@ConditionalOnProperty(name = "instituicao.raft.enabled", havingValue = "true", matchIfMissing = true)
public class RaftModeConfig {

    /** O nó é fechado no shutdown do Spring (fecha RaftClient + RaftServer). */
    @Bean(destroyMethod = "close")
    public NoInstituicao noInstituicao(@Value("${instituicao.node-id:n1}") String nodeId) throws IOException {
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
