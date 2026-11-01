package com.campuslink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 定时任务设施（CR-058 首次引入）：ADR-006 的选型是「{@code @Scheduled} + Redis 锁」，
 * 在此之前全仓没有任何调度设施（无 {@code @Scheduled} / {@code @EnableScheduling}、pom 无 quartz）。
 *
 * <p>{@code @EnableScheduling} 放这里而不是启动类：启动类是全局入口，
 * "本应用打开了哪些基础设施"集中在 config 包，后来者不必去启动类翻注解。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * 显式声明调度器而不是用 Spring 的默认实例：当前有热榜刷新（10 分钟）与搜索索引同步（30 秒）两个任务，
     * 池大小 2 让两者互不阻塞——搜索首次全量重建可能占用调度线程较久，不能把热榜刷新一起卡住；
     * 多实例的重复执行仍由各自的 Redis advisory 锁兜底。
     * 显式声明的另一个好处是给线程命名，日志里能一眼认出"这行来自调度线程还是请求线程"。
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("cl-sched-");
        return scheduler;
    }
}
