package com.campuslink.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * T2：契约快照的**真身语义比对**（[CR-106](../../../docs/开发/实施方案/CR-106-快照纳入CI比对.md)，与
 * {@code OpenApiSnapshotOperationsTest}（T1）合成两层机器拦截，处置问题清单 E-001）。
 *
 * <p><b>类名必须叫 {@code ...Test} 而不是 {@code ...IT}</b>：surefire 默认包含模式是 {@code **\/Test*.java}、
 * {@code **\/*Test.java}、{@code **\/*Tests.java}、{@code **\/*TestCase.java}，<b>{@code *IT} 后缀根本不会被执行</b>——
 * 本类起初命名为 {@code OpenApiSnapshotEqualityIT}，用 {@code -Dtest=} 显式点名时是绿的、默认 {@code mvn verify}
 * 里<b>一行都没跑</b>（连 SKIPPED 都不计），改名是 CR-090 {@code SpringBootContextTest} 的同一口径。
 *
 * <p><b>它补的是 T1 看不见的字段级漂移</b>：T1 只比「哪些操作存在、各自公开还是受保护」，
 * 而 [CR-104](../../../docs/变更日志/变更台账.md#cr-104) 那种「给 {@code UserProfileVo} 加两个计数字段却忘了重抓」
 * 在 T1 里是完全绿的——出参形状变了、操作没变。只有起真上下文抓 {@code /api/docs} 才看得见。
 *
 * <p><b>为什么挂在 {@code SPRING_IT} 门下而不是默认跑</b>：真上下文要连 MySQL（Flyway 启动即迁移）与 Redis，
 * 这是 CR-090 已经立好的口径——默认 {@code mvn verify} 跳过、CI 侧由 services 提供后以
 * {@code SPRING_IT=true mvn -B verify} 真跑。<b>代价如实记一句</b>：本机不带环境变量跑 verify 时，
 * 字段级校验等于没跑，那一段仍靠人（所以 T1 必须永远跑，它是下限）。
 *
 * <p><b>比语义不比字节</b>（三条噪声源，逐条剔除而不是全文归一）：
 * ① {@code servers}——springdoc 把它回显成**请求 Host**，本用例走的是 {@code localhost:$随机端口}，
 *    归档里是 {@code localhost:8088}（契约 README §2 的方法学备注记过两次，用 127.0.0.1 抓会得到另一个值）；
 * ② 缩进与键序——归档由 {@code json.dumps(indent=2)} 格式化、键序保持 springdoc 原序，
 *    比字节会因缩进不同天天假红，故两侧都交给 Jackson 解析后比<b>语义</b>：
 *    {@code ObjectNode.equals} 是<b>无序</b>的 map 相等、{@code ArrayNode.equals} <b>有序</b>，
 *    正好符合"字段集合要对、响应顺序也要对"的口径；
 * ③ 不比时间戳——快照里没有生成时间字段（v1.14 那次实测确认过），所以不需要额外剔除。
 *
 * <p>差异只报<b>第一处路径</b>与两侧规模，不 dump 全文：整个文档 12 万字节，贴出来会淹没结论。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "SPRING_IT", matches = "true")
@TestPropertySource(properties = {
        "campuslink.hot.initial-delay-ms=3600000",
        "campuslink.hot.refresh-interval-ms=3600000",
        "campuslink.account.anonymization.enabled=false",
})
class OpenApiSnapshotEqualityTest {

    private static final String SNAPSHOT_DEFAULT = "../docs/设计/接口契约/openapi.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("真上下文抓 /api/docs 与归档快照语义相等（servers 除外）：字段级漂移到此为止")
    void liveDocumentEqualsArchivedSnapshot() throws IOException, InterruptedException {
        JsonNode live = fetchLiveDocument();
        JsonNode archived = MAPPER.readTree(snapshotPath().toFile());

        // 只剔除已知的抓取方式噪声，其余一律要求逐字段相等
        ((ObjectNode) live).remove("servers");
        ((ObjectNode) archived).remove("servers");

        if (!live.equals(archived)) {
            String firstDiff = firstDifference(live, archived, "$");
            Assertions.fail("契约快照与运行中服务不一致（**改接口未重抓快照**，或反之）。"
                    + "第一处差异：" + firstDiff
                    + "；规模 实时=路径" + count(live, "paths") + "/操作" + operations(live)
                    + "，归档=路径" + count(archived, "paths") + "/操作" + operations(archived)
                    + "。按 AGENTS「测试约定」：起后端按契约 README §2 命令重抓 docs/设计/接口契约/openapi.json "
                    + "并同步技术方案 §5 与该 README。");
        }

        Assertions.assertTrue(operations(live) >= 40, "抓到的文档规模异常小，宁可信其坏：" + operations(live));
    }

    private JsonNode fetchLiveDocument() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/docs"))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode(), "抓不到 /api/docs（springdoc 装配或路径变了）");
        Assertions.assertFalse(response.body().isBlank(), "/api/docs 返回空体");
        return MAPPER.readTree(response.body());
    }

    private Path snapshotPath() {
        Path path = Paths.get(System.getProperty("openapi.snapshot.path", SNAPSHOT_DEFAULT));
        Assertions.assertTrue(path.toFile().exists(),
                "找不到归档快照 " + path.toAbsolutePath() + "（可用 -Dopenapi.snapshot.path=… 覆盖）");
        return path;
    }

    /**
     * 递归找第一处差异，路径形如 {@code $.paths['/api/v1/users/{id}'].get.responses['200']}。
     * 只为把失败信息压成一行人能读的坐标——不做全量 diff 报告（那是 IDE 的活）。
     */
    private String firstDifference(JsonNode a, JsonNode b, String at) {
        if (a.getNodeType() != b.getNodeType()) {
            return at + "（节点类型不同：" + a.getNodeType() + " vs " + b.getNodeType() + "）";
        }
        if (a.isObject()) {
            java.util.Iterator<String> names = a.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!b.has(name)) {
                    return at + "." + name + "（归档里缺这个字段）";
                }
                String deeper = firstDifference(a.get(name), b.get(name), at + "." + name);
                if (deeper != null) {
                    return deeper;
                }
            }
            java.util.Iterator<String> others = b.fieldNames();
            while (others.hasNext()) {
                String name = others.next();
                if (!a.has(name)) {
                    return at + "." + name + "（运行中服务里没有这个字段）";
                }
            }
            return null;
        }
        if (a.isArray()) {
            if (a.size() != b.size()) {
                return at + "（数组长度不同：" + a.size() + " vs " + b.size() + "）";
            }
            for (int i = 0; i < a.size(); i++) {
                String deeper = firstDifference(a.get(i), b.get(i), at + "[" + i + "]");
                if (deeper != null) {
                    return deeper;
                }
            }
            return null;
        }
        return a.equals(b) ? null : at + "（值不同：" + a.asText() + " vs " + b.asText() + "）";
    }

    private long operations(JsonNode root) {
        long count = 0;
        for (JsonNode pathItem : root.path("paths")) {
            for (String verb : new String[]{"get", "post", "put", "delete", "patch"}) {
                if (pathItem.has(verb)) {
                    count++;
                }
            }
        }
        return count;
    }

    private int count(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isObject() ? node.size() : 0;
    }
}
