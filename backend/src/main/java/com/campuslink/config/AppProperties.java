package com.campuslink.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "campuslink")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Crypto crypto = new Crypto();
    private Captcha captcha = new Captcha();
    private Verify verify = new Verify();
    private Roster roster = new Roster();
    private CodeSender codeSender = new CodeSender();
    private Cors cors = new Cors();
    private Hot hot = new Hot();
    private Ai ai = new Ai();
    private Search search = new Search();

    @Data
    public static class Jwt {
        private String secret;
        private int ttlDays = 7;
    }

    @Data
    public static class Crypto {
        private String hashKey;
        private String cryptKey;
    }

    @Data
    public static class Captcha {
        private int ttlMinutes = 5;
        private int resendIntervalSeconds = 60;
        private int dailyLimit = 10;
        /**
         * 固定验证码（开发联调便利）：配置后所有验证码都为该值，**非本机环境必须留空**（上线检查清单项）。
         * 刻意与 code-sender.mode 解耦，避免"日志模式"被隐式等同于"弱口令"。
         */
        private String fixedCode;
    }

    @Data
    public static class Verify {
        private int ipHourlyLimit = 10;
        private int ticketTtlMinutes = 5;
    }

    @Data
    public static class Roster {
        /** 学籍核验旁路（内置测试名册）：仅限开发联调，生产必须为 false（上线检查清单项） */
        private boolean bypass = true;
    }

    @Data
    public static class CodeSender {
        private String mode = "log";
    }

    @Data
    public static class Cors {
        private String allowedOrigins = "http://localhost:5173";
    }

    /**
     * AI-assisted features (currently: similar-post recommendation at publish time).
     * All sub-features default to enabled with conservative thresholds; disable via config without code change.
     */
    @Data
    public static class Ai {
        private Similar similar = new Similar();

        @Data
        public static class Similar {
            /** Feature toggle: when false, findSimilarPosts always returns an empty list (silent degradation) */
            private boolean enabled = true;
            /** Minimum title length (chars) to trigger a lookup; shorter titles yield empty results without hitting the DB */
            private int minTitleLength = 6;
            /** Maximum number of similar posts to return */
            private int maxResults = 3;
        }
    }

    /**
     * 搜索实现（ADR-004 / CR-181）：{@link Engine#MYSQL} 是 CR-136 起的现行口径（MySQL ngram FULLTEXT，
     * 标题 + 正文 + 标签）；{@link Engine#MEILISEARCH} 是 PRD F-FORUM-009 预留的规模升级。
     *
     * <p>同步周期与首轮延迟**刻意不在这里绑定**——它们只被 {@code SearchIndexScheduler} 的
     * {@code @Scheduled} 占位符读取，再绑一份就成了两个事实源（与 {@link Hot} 同一纪律）。
     */
    @Data
    public static class Search {
        private Engine engine = Engine.MYSQL;
        private Meili meili = new Meili();
        /** 索引重建 / 增量同步的单批条数：1000 条一次 HTTP，兼顾任务开销与内存 */
        private int batchSize = 1000;
        /** 同步锁 TTL（秒）：advisory 锁不显式释放、靠 TTL 兜底，须 ≤ 同步周期 */
        private long lockTtlSeconds = 30L;

        public enum Engine {
            MYSQL, MEILISEARCH
        }

        @Data
        public static class Meili {
            private String url = "http://127.0.0.1:7700";
            /** 开发期本机 Meili 可不开 master key（仅绑定 127.0.0.1）；生产必须注入，由 ProductionSafetyGuard 强制 */
            private String apiKey = "";
            private String index = "posts_v1";
        }
    }

    /**
     * 热榜（ADR-006）：权重与 λ 是**待试点数据调优**的参数（PRD Q6），不是结论，故全部可配、调参不改代码。
     *
     * <p>刷新周期与首轮延迟**刻意不在这里绑定**——它们只被 {@code @Scheduled} 的属性占位符读取
     * （{@code campuslink.hot.refresh-interval-ms} / {@code initial-delay-ms}），再绑一份就成了两个事实源，
     * 改了一处另一处静默不动。默认值见 {@code application.yml} 与 {@code HotScoreRefreshScheduler} 的占位符。
     */
    @Data
    public static class Hot {
        /** 回复权重最高：它是论坛的核心互动且成本最高（要读、要想、要写） */
        private double replyWeight = 3d;
        private double likeWeight = 1d;
        /** 收藏介于两者之间：成本高于点赞（要回访），但互动性弱于回复 */
        private double favoriteWeight = 2d;
        /** 每小时衰减率 λ（ADR-006 原文值）：20 小时衰减到 e^-1 ≈ 37% */
        private double decayPerHour = 0.05d;
        /** 刷新窗口（天）：只重算窗口内的帖子，窗口外置 0——指数衰减只降不消，不归零则老帖永久霸榜 */
        private int windowDays = 30;
        /** 单批读取条数：分批读—算—写回，避免一次把整窗候选读进内存 */
        private int batchSize = 500;
        /** 锁 TTL（秒）：advisory 锁不显式释放、靠 TTL 兜底，故须略大于单轮预期耗时 */
        private long lockTtlSeconds = 300L;
    }
}
