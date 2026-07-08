package instituicao.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import estruturas.db.BancoDeDados;
import raft.AplicadorDeContas;
import raft.AplicadorLocal;

/**
 * Modo local (processo único, sem consenso): escreve direto num banco em memória.
 *
 * <p>Ativo quando {@code instituicao.raft.enabled=false}. Útil para desenvolver
 * e testar o endpoint REST sem precisar subir maioria de nós.
 */
@Configuration
@ConditionalOnProperty(name = "instituicao.raft.enabled", havingValue = "false")
public class LocalModeConfig {

    @Bean
    public BancoDeDados banco() {
        return new BancoDeDados();
    }

    @Bean
    public AplicadorDeContas aplicador(BancoDeDados banco) {
        return new AplicadorLocal(banco);
    }
}
