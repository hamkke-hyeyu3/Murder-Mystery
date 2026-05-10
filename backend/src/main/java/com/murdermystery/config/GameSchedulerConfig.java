package com.murdermystery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class GameSchedulerConfig {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService gameScheduler() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "game-sched");
            t.setDaemon(true);
            return t;
        });
    }
}
