package com.campuslink.contract;

import com.campuslink.common.web.PublicEndpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * T1：契约快照的**操作集机器校验**（[CR-106](../../../docs/开发/实施方案/CR-106-快照纳入CI比对.md)，处置
 * [问题清单](../../../docs/项目现有问题清单.md) E-001「快照无机器校验，漂移只能靠约定拦」）。
 *
 * <p><b>它拦的是本仓最容易复发的那一类漂移</b>：改了 Controller 却忘了重抓快照。这不是假想——
 * [CR-077](../../../docs/变更日志/变更台账.md#cr-077) / [CR-078](../../../docs/变更日志/变更台账.md#cr-078) 落地时欠了两轮，
 * 由 CR-080 补做才对齐；此前"快照等于代码"只能靠人记得重抓 + 事后手工 {@code cmp}。
 *
 * <p><b>为什么本类刻意不依赖 Spring 上下文、不连数据库</b>：它是 {@code mvn verify} 默认就会跑的守卫。
 * 一旦把它挂在真上下文上（那需要 MySQL / Redis），它就会像 CR-090 的 SPRING_IT 四例一样长期 SKIPPED，
 * 守卫等于不存在。字段级漂移（给 VO 加一个组件）本类看不见，那由
 * {@code OpenApiSnapshotEqualityTest}（T2，需真上下文）补上——**两层各守一头，别把 T1 当成全量校验**。
 *
 * <p><b>比对口径（三条，都是为避免假红而定）</b>：
 * ① 比 <em>operationId 之外的稳定标识</em>：{@code "METHOD 路径模板"}，路径模板直接取注解里的字面量
 *    （springdoc 的 {@code paths} 键就是这个模板，两者本就同源）；
 * ② 比每个操作**是否受保护**（快照侧看 {@code security} 是否非空，代码侧看方法上有没有
 *    {@code @SecurityRequirement}）——这一格防的是"把公开端点顺手改成受保护"或反之，
 *    它与 {@code ArchitectureGuardTest} 的 G7 互补：G7 守"声明齐不齐"，本类守"声明与归档快照对不对得上"；
 * ③ **不比 responses / schema 内容**：契约 README §6.3 已登记若干 springdoc 表达极限
 *    （{@code 1002/1003/1004} 三类响应进不了快照、可重复 {@code @Pattern} 不输出），拿快照的已知短板当缺陷会天天红。
 */
class OpenApiSnapshotOperationsTest {

    /** 快照只有一个事实源：docs/ 下那份归档。**刻意不复制进 src/test/resources**——两处存一份必然漂移。 */
    private static final String SNAPSHOT_DEFAULT = "../docs/设计/接口契约/openapi.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("操作集逐字对齐：代码里每个映射方法都在快照里、快照里每个操作都能回到代码（双向差集都空）")
    void operationSetMatchesArchivedSnapshot() throws IOException {
        Map<String, Boolean> fromCode = operationsFromCode();
        Map<String, Boolean> fromSnapshot = operationsFromSnapshot(snapshot());

        Set<String> onlyInCode = new TreeSet<>(fromCode.keySet());
        onlyInCode.removeAll(fromSnapshot.keySet());
        Set<String> onlyInSnapshot = new TreeSet<>(fromSnapshot.keySet());
        onlyInSnapshot.removeAll(fromCode.keySet());

        Assertions.assertTrue(onlyInCode.isEmpty(),
                () -> "代码里有 " + onlyInCode.size() + " 个映射方法不在归档快照里 —— **契约已变，按 AGENTS「测试约定」"
                        + "起后端重抓 docs/设计/接口契约/openapi.json 并同步技术方案 §5 与本行报错清单**：" + onlyInCode);
        Assertions.assertTrue(onlyInSnapshot.isEmpty(),
                () -> "归档快照里有 " + onlyInSnapshot.size() + " 个操作在代码里找不到（端点被删/改名却没重抓快照）：" + onlyInSnapshot);

        List<String> flagMismatch = new ArrayList<>();
        fromCode.forEach((op, protectedInCode) -> {
            Boolean protectedInSnapshot = fromSnapshot.get(op);
            if (protectedInSnapshot != null && !protectedInSnapshot.equals(protectedInCode)) {
                flagMismatch.add(op + "：代码侧 " + (protectedInCode ? "受保护" : "公开")
                        + "，快照侧 " + (protectedInSnapshot ? "受保护" : "公开"));
            }
        });
        Assertions.assertTrue(flagMismatch.isEmpty(),
                () -> "公开/受保护标记与快照不符（鉴权口径进了契约，改一端点要两边一起动）：" + flagMismatch);
    }

    @Test
    @DisplayName("规模不为零且与快照一致（防「扫到一个映射方法都没有」这种假绿）")
    void nonZeroAndConsistentScale() throws IOException {
        Map<String, Boolean> fromCode = operationsFromCode();
        JsonNode root = snapshot();
        long snapshotOps = countSnapshotOperations(root);

        Assertions.assertTrue(fromCode.size() >= 40,
                "扫到的映射方法异常少（" + fromCode.size() + "）——多半是包名/注解扫描失效，宁可信其坏");
        Assertions.assertEquals(snapshotOps, fromCode.size(),
                "操作数与快照不一致：代码 " + fromCode.size() + " / 快照 " + snapshotOps);
    }

    /** 归档快照本身规模：paths × {get,post,put,delete,patch}。 */
    private long countSnapshotOperations(JsonNode root) {
        long count = 0;
        for (JsonNode pathItem : root.path("paths")) {
            for (String verb : HTTP_VERBS) {
                if (pathItem.has(verb)) {
                    count++;
                }
            }
        }
        return count;
    }

    private JsonNode snapshot() throws IOException {
        Path path = Paths.get(System.getProperty("openapi.snapshot.path", SNAPSHOT_DEFAULT));
        if (!path.toFile().exists()) {
            Assertions.fail("找不到归档快照 " + path.toAbsolutePath()
                    + "（surefire 工作目录是 backend/，默认相对路径 ../docs/设计/接口契约/openapi.json；"
                    + "如需覆盖可传 -Dopenapi.snapshot.path=…）");
        }
        return MAPPER.readTree(path.toFile());
    }

    private Map<String, Boolean> operationsFromSnapshot(JsonNode root) {
        Map<String, Boolean> ops = new LinkedHashMap<>();
        JsonNode paths = root.path("paths");
        paths.fields().forEachRemaining(entry -> {
            JsonNode pathItem = entry.getValue();
            for (String verb : HTTP_VERBS) {
                if (pathItem.has(verb)) {
                    String key = verb.toUpperCase() + " " + entry.getKey();
                    boolean protectedOp = pathItem.get(verb).path("security").isArray()
                            && !pathItem.get(verb).path("security").isEmpty();
                    if (ops.put(key, protectedOp) != null) {
                        Assertions.fail("快照里同一操作出现两次（不该发生）：" + key);
                    }
                }
            }
        });
        return ops;
    }

    /** 扫 com.campuslink 下所有 Controller / RestController 的映射注解，得「METHOD 路径模板 → 是否受保护」。 */
    private Map<String, Boolean> operationsFromCode() {
        // 默认实现已要求"独立且可实例化"（getMetadata().isIndependent() && isConcrete()），不必覆写
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class, true));

        Map<String, Boolean> ops = new LinkedHashMap<>();
        scanner.findCandidateComponents("com.campuslink").forEach(candidate -> {
            Class<?> type = load(candidate.getBeanClassName());
            if (type.getAnnotation(RestController.class) == null && type.getAnnotation(Controller.class) == null) {
                return;
            }
            for (String classPattern : patternsOf(AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class))) {
                for (Method method : type.getDeclaredMethods()) {
                    collectMethodMappings(type, method, classPattern, ops);
                }
            }
        });
        return ops;
    }

    private void collectMethodMappings(Class<?> type, Method method, String classPattern, Map<String, Boolean> ops) {
        record Mappings(String verb, String[] paths) {
        }
        List<Mappings> found = new ArrayList<>();
        RequestMapping merged = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (merged != null) {
            for (org.springframework.web.bind.annotation.RequestMethod rm : merged.method()) {
                found.add(new Mappings(rm.name(), merged.path().length > 0 ? merged.path() : merged.value()));
            }
        }
        GetMapping get = AnnotatedElementUtils.findMergedAnnotation(method, GetMapping.class);
        if (get != null) {
            found.add(new Mappings("GET", paths(get, type, method)));
        }
        PostMapping post = AnnotatedElementUtils.findMergedAnnotation(method, PostMapping.class);
        if (post != null) {
            found.add(new Mappings("POST", paths(post, type, method)));
        }
        PutMapping put = AnnotatedElementUtils.findMergedAnnotation(method, PutMapping.class);
        if (put != null) {
            found.add(new Mappings("PUT", paths(put, type, method)));
        }
        DeleteMapping del = AnnotatedElementUtils.findMergedAnnotation(method, DeleteMapping.class);
        if (del != null) {
            found.add(new Mappings("DELETE", paths(del, type, method)));
        }
        PatchMapping patch = AnnotatedElementUtils.findMergedAnnotation(method, PatchMapping.class);
        if (patch != null) {
            found.add(new Mappings("PATCH", paths(patch, type, method)));
        }
        if (found.isEmpty()) {
            return;
        }
        boolean protectedOp = AnnotatedElementUtils.hasAnnotation(method, SecurityRequirement.class);
        for (Mappings m : found) {
            for (String p : m.paths()) {
                String key = m.verb() + " " + join(classPattern, p);
                Boolean previous = ops.putIfAbsent(key, protectedOp);
                if (previous != null && !previous.equals(protectedOp)) {
                    Assertions.fail("同一操作在代码里既有公开又有受保护声明：" + key);
                }
            }
        }
    }

    /** 具体映射注解（GetMapping 等）没有统一父接口，只能逐个取 value/path。 */
    private String[] paths(Annotation annotation, Class<?> type, Method method) {
        try {
            String[] value = (String[]) annotation.annotationType().getMethod("value").invoke(annotation);
            String[] path = (String[]) annotation.annotationType().getMethod("path").invoke(annotation);
            String[] picked = path.length > 0 ? path : value;
            // 空路径（如类级前缀上的 @GetMapping 不带参数）必须归一成 [""]，否则整个方法被静默丢掉——
            // 本仓有 8 个操作是这种写法，漏掉就会把"代码里没有"误报成"快照多写了"
            return picked.length == 0 ? new String[]{""} : picked;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("取映射注解路径失败：" + type.getName() + "#" + method.getName(), e);
        }
    }

    private String[] patternsOf(RequestMapping mapping) {
        if (mapping == null) {
            return new String[]{""};
        }
        String[] paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
        return paths.length == 0 ? new String[]{""} : paths;
    }

    /** 路径模板拼接：与 Spring 的组合规则一致，且把前导斜杠补齐、去掉多余斜杠（快照键就是这个形式）。 */
    private String join(String base, String sub) {
        String b = base == null ? "" : base.trim();
        String s = sub == null ? "" : sub.trim();
        if (!b.isEmpty() && !b.startsWith("/")) {
            b = "/" + b;
        }
        if (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        if (s.isEmpty() || s.equals("/")) {
            return b.isEmpty() ? "/" : b;
        }
        if (!s.startsWith("/")) {
            s = "/" + s;
        }
        return b + s;
    }

    private Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("扫描到类却无法加载：" + className, e);
        }
    }

    private static final String[] HTTP_VERBS = {"get", "post", "put", "delete", "patch"};
}
