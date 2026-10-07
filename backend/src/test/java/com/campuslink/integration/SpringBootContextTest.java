package com.campuslink.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 最小集成测试（E-001 / CR-090）：真实 Spring 上下文 + 真实 MySQL / Redis——Flyway、`@MapperScan`
 * 实际扫描结果、`SecurityFilterChain` 过滤器链此前**只有真机人肉验证**兜底（问题清单 E-001），
 * 本类把它们首次装进测试。
 *
 * <p><b>运行条件</b>：类级 {@code @EnabledIfEnvironmentVariable(SPRING_IT=true)}——默认
 * {@code mvn verify} 跳过（CI 无 MySQL / Redis 服务，跳过不破坏 CI），本机起栈后以
 * {@code SPRING_IT=true mvn verify} 真机运行。CI 侧服务注入见 CR-090 方案 §5 备料（CI 配置属红线，待批）。
 *
 * <p><b>调度副作用防御</b>：热榜任务的周期与首轮延迟经测试属性推至 1h 之外；匿名化任务经
 * {@code campuslink.account.anonymization.enabled=false} 关闭（CR-090 加的装配开关，
 * matchIfMissing=true 保证生产行为零变化）。测试全部只读或被过滤器链拒绝，不写库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_IT", matches = "true")
@TestPropertySource(properties = {
        "campuslink.hot.initial-delay-ms=3600000",
        "campuslink.hot.refresh-interval-ms=3600000",
        "campuslink.account.anonymization.enabled=false",
})
class SpringBootContextTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("上下文加载：Bean 装配 / @MapperScan 实际扫描 / SecurityFilterChain 构造全部成功（本用例本身即断言）")
    void contextLoads() {
    }

    @Test
    @DisplayName("Flyway：迁移在真实库全部执行成功（无 success<>1 的历史行，迁移数 ≥ V1~V4）")
    void flywayMigrationsAllSucceeded() {
        Integer failed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success <> 1", Integer.class);
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
        assertThat(failed).isZero();
        assertThat(total).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("公开读 + Mapper + 真库：匿名 GET /api/v1/boards 返回 6 个启用版块")
    void anonymousBoardListWorks() throws Exception {
        mockMvc.perform(get("/api/v1/boards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(6));
    }

    @Test
    @DisplayName("过滤器链 fail-closed：匿名 POST /api/v1/posts 在进入业务代码前被拒 401 / 4001")
    void anonymousPostIsRejectedByFilterChain() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardCode\":\"qna\",\"title\":\"x\",\"contentMd\":\"y\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(4001));
    }
}
