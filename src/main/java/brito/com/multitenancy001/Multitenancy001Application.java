package brito.com.multitenancy001;

import brito.com.multitenancy001.infrastructure.startup.DatabaseMissingFailFastListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
//@EnableCaching
@EnableScheduling
public class Multitenancy001Application {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(Multitenancy001Application.class);
		app.addListeners(new DatabaseMissingFailFastListener());
		app.run(args);
	}

}
