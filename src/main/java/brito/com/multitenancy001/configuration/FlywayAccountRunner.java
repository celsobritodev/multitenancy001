package brito.com.multitenancy001.configuration;

import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlywayAccountRunner implements ApplicationRunner {

    private final Flyway flywayAccount;
    
   

    @Override
    public void run(ApplicationArguments args) {
    	
   
    	
    	
        log.info("🚀 Executando Flyway ACCOUNT (public)");
        flywayAccount.migrate();
    }
}
