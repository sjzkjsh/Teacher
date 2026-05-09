package com.example.aiteacher.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "documentLoadExecutor")
    public Executor documentLoadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);             // 核心线程数，可根据需要调整
        executor.setMaxPoolSize(4);              // 最大线程数（I/O密集可调大）
        executor.setQueueCapacity(50);           // 缓冲队列
        executor.setThreadNamePrefix("doc-load-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // 拒绝策略：让提交任务的线程自己执行
        executor.setWaitForTasksToCompleteOnShutdown(true); // 关闭时等待任务完成
        executor.setAwaitTerminationSeconds(60); // 等待超时，默认0（不等待）
        executor.initialize();
        return executor;
    }
}