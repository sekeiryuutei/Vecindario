package com.codevam.vecindad.reconciliation.adapter.out;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ReconciliationExecutorConfig {

    /** Cola acotada: si se llena, la carga se rechaza con un mensaje claro en vez de agotar la memoria. */
    @Bean(name = "reconciliationExecutor")
    public ThreadPoolTaskExecutor reconciliationExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(2);
        ex.setQueueCapacity(20);
        ex.setThreadNamePrefix("recon-");
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(30);
        return ex;
    }
}
