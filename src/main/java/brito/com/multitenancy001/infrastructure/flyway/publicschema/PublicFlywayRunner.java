package brito.com.multitenancy001.infrastructure.flyway.publicschema;

import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ⚠️ Por padrão DESLIGADO. O Flyway do PUBLIC deve rodar no bootstrap do Spring.
 * Ligue somente se você quiser forçar migração manual (não recomendado).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "app.flyway.public.manual", havingValue = "true")
public class PublicFlywayRunner implements ApplicationRunner {

    private final Flyway flyway;

    @Override
    public void run(ApplicationArguments args) {
        log.info("⚠️ app.flyway.public.manual=true -> Executando Flyway PUBLIC manualmente (não recomendado)");
        flyway.migrate();
    }
}
