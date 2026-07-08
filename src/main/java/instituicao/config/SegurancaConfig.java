package instituicao.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Beans de segurança. Usa apenas {@code spring-security-crypto} (BCrypt) para o
 * hash de senha — sem o starter-security inteiro, então não há filtros de
 * autenticação automáticos: a autorização é feita à mão no controller via token.
 */
@Configuration
public class SegurancaConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
