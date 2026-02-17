package brito.com.multitenancy001.infrastructure.startup;

import java.sql.SQLException;

import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

public class DatabaseMissingFailFastListener implements ApplicationListener<ApplicationFailedEvent> {

    private static final String SQLSTATE_DB_DOES_NOT_EXIST = "3D000";

    @Override
    public void onApplicationEvent(ApplicationFailedEvent event) {
        Throwable root = rootCause(event.getException());

        SQLException sqlEx = findSqlException(root);
        if (sqlEx == null) return;

        if (!SQLSTATE_DB_DOES_NOT_EXIST.equals(sqlEx.getSQLState())) return;

        String dbName = extractDbName(sqlEx.getMessage());

        String url = null;
        String user = null;

        try {
            if (event.getApplicationContext() != null) {
                var env = event.getApplicationContext().getEnvironment();
                url = env.getProperty("spring.datasource.url");
                user = env.getProperty("spring.datasource.username");
            } else {
                // fallback: em alguns casos extremos o context pode não existir
                ConfigurableEnvironment env = event.getSpringApplication().run().getEnvironment();
                url = env.getProperty("spring.datasource.url");
                user = env.getProperty("spring.datasource.username");
            }
        } catch (Throwable ignored) {
            // não interfere em nada caso não consiga ler env
        }

        System.err.println();
        System.err.println("============================================================");
        System.err.println("❌ Não foi possível iniciar a aplicação");
        System.err.println();
        System.err.println("Motivo: o banco de dados do PostgreSQL"
                + (dbName != null ? " \"" + dbName + "\"" : "") + " não existe.");
        System.err.println();

        if (url != null && !url.isBlank()) {
            System.err.println("Config atual:");
            System.err.println("  - spring.datasource.url = " + url);
            if (user != null && !user.isBlank()) {
                System.err.println("  - spring.datasource.username = " + user);
            }
            System.err.println();
        }

        System.err.println("✅ Como resolver:");
        System.err.println("  1) Crie o banco:");
        System.err.println("     createdb -U postgres " + (dbName != null ? dbName : "<NOME_DO_BANCO>"));
        System.err.println("  2) Ou ajuste a URL do datasource no application-dev.properties");
        System.err.println();
        System.err.println("Encerrando a aplicação (fail-fast).");
        System.err.println("============================================================");
        System.err.println();

        System.exit(2);
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur != null && cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur != null ? cur : t;
    }

    private static SQLException findSqlException(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof SQLException se) return se;
            cur = cur.getCause();
        }
        return null;
    }

    private static String extractDbName(String msg) {
        if (msg == null) return null;
        int i = msg.indexOf('"');
        if (i < 0) return null;
        int j = msg.indexOf('"', i + 1);
        if (j < 0) return null;
        String inside = msg.substring(i + 1, j).trim();
        return inside.isBlank() ? null : inside;
    }
}
