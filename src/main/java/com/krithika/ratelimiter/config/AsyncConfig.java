package com.krithika.ratelimiter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Enables real asynchronous execution for usage logging / alerting so that
 * persistence work runs OFF the request thread and never inflates the
 * rate-limit decision latency.
 *
 * Why this is needed:
 *   @Async only takes effect when (a) @EnableAsync is present and (b) the
 *   annotated method is invoked through the Spring proxy (i.e. from a *different*
 *   bean). Without @EnableAsync the annotation is silently ignored and the work
 *   runs synchronously on the caller's thread.
 *
 * The pool is bounded with a caller-runs fallback: if logging ever falls behind,
 * back-pressure is applied instead of growing an unbounded queue and risking OOM.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "usageExecutor")
    public Executor usageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("usage-async-");
        // If the queue fills, run on the calling thread rather than dropping work.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
