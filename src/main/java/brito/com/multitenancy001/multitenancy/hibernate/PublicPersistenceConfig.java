package brito.com.multitenancy001.multitenancy.hibernate;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManagerFactory;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "brito.com.multitenancy001.repositories.platform",
    entityManagerFactoryRef = "publicEntityManagerFactory",
    transactionManagerRef = "publicTransactionManager"
)
public class PublicPersistenceConfig {

    @Bean(name = "publicTransactionManager")
    public PlatformTransactionManager publicTransactionManager(
            @Qualifier("publicEntityManagerFactory") 
            EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}


