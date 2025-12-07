package com.smartroute.smartroute1.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class SyncAsyncConfig {

    /**
     * Executor for handling mail-related asynchronous tasks.
     * Otherwise, sending emails can block main application threads.
     *
     * @return An Executor with a fixed thread pool of 4 threads.
     */
    @Bean(name = "mailExecutor")
    public Executor mailExecutor() {
        return Executors.newFixedThreadPool(4);
    }

}
