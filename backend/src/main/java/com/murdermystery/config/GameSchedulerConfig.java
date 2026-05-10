package com.murdermystery.config;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
public class GameSchedulerConfig {

    private ScheduledExecutorService gameScheduler;

    @Bean
    public ScheduledExecutorService gameScheduler() {
        gameScheduler = Executors.newScheduledThreadPool(2, r -> new Thread(r, "game-sched"));
        return gameScheduler;
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        gameScheduler.shutdown();
        if (!gameScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
            gameScheduler.shutdownNow();
        }
    }
}
