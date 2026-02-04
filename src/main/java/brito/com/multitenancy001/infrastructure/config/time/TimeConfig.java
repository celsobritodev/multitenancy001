package brito.com.multitenancy001.infrastructure.config.time;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clock único do sistema.
 * Regra: UTC para correlação perfeita entre logs, auditoria e eventos.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

