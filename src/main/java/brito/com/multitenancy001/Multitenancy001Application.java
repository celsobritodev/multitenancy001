package brito.com.multitenancy001;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
//@EnableCaching
@EnableScheduling
public class Multitenancy001Application {

	public static void main(String[] args) {
		SpringApplication.run(Multitenancy001Application.class, args);
	}

}

