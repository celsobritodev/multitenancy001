package brito.com.multitenancy001.infrastructure.tx;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuração de infraestrutura para tarefas executadas após completion de transações.
 *
 * <p>Este executor é usado para:</p>
 * <ul>
 *   <li>Auditoria PUBLIC (SOC2-like) executada fora do thread de commit/cleanup</li>
 *   <li>Provisionamento de {@code public.login_identities} fora do tenant TX</li>
 * </ul>
 *
 * <p><b>Importante:</b> mantemos um pool dedicado para não competir com workloads HTTP.</p>
 */
@Configuration
@EnableAsync
public class AfterTxAsyncConfig {

    /**
     * Executor dedicado para tarefas "after completion".
     *
     * <p>Nome do bean é usado por {@code @Qualifier("afterTxCompletionExecutor")} e {@code @Async("afterTxCompletionExecutor")}.</p>
     */
    @Bean(name = "afterTxCompletionExecutor")
    public TaskExecutor afterTxCompletionExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setThreadNamePrefix("after-tx-");
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(8);
        exec.setQueueCapacity(10_000);
        exec.initialize();
        return exec;
    }
}