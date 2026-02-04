package brito.com.multitenancy001.infrastructure.flyway.publicschema;

import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PublicFlywayRunner implements ApplicationRunner {

	private final Flyway flywayPublic;

	@Override
	public void run(ApplicationArguments args) {

		log.info("🚀 Executando Flyway ACCOUNT (schema public)");
		flywayPublic.migrate();
	}
}

