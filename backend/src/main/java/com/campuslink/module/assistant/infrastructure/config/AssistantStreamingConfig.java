package com.campuslink.module.assistant.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模型流专用线程池（CR-200）：上游读取是阻塞 I/O，不能占 Tomcat 请求线程。
 *
 * <p>有界队列 + AbortPolicy：排队满时直接拒绝并返回 7006，避免无限堆积把内存和上游配额一起拖垮。
 */
@Configuration
public class AssistantStreamingConfig {

    @Bean(name = "assistantStreamExecutor", destroyMethod = "shutdown")
    public ExecutorService assistantStreamExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "assistant-stream-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(4, 8, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100), factory, new ThreadPoolExecutor.AbortPolicy());
    }
}
