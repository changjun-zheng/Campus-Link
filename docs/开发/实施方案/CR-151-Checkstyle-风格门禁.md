# CR-151 · 后端 Checkstyle 风格门禁（E-003 最后一块）

| 文档信息 | 内容 |
|---------|------|
| 版本 | v1.1 |
| 状态 | 已实施（2026-10-26，发起人选定「按方案实施」） |
| 维护人 | AI 会话（2026-10-26） |
| 最后更新 | 2026-10-26 |
| 关联 CR | [CR-151](../../变更日志/变更台账.md#cr-151) |

## 1. 目标

把问题清单 **E-003** 剩下的「Checkstyle / PMD 仍无」中的 **Checkstyle** 接入 `mvn verify`：
让「导入规范 / 花括号 / 基础编码 / 文件格式」从人工约定变成**机器门禁**，且 **CI 零改动**（`backend-ci` 已跑 `mvn -B verify`）。

## 2. 范围

- **做**：
  - `backend/pom.xml` 增加 `maven-checkstyle-plugin` **3.6.0**，`check` 目标绑 `verify`，**只扫 `src/main/java`**、`failOnViolation=true`；
  - 新增 `backend/config/checkstyle/checkstyle.xml`，**最小高信号规则集 14 条**：
    `AvoidStarImport` · `UnusedImports` · `RedundantImport` · `IllegalImport`（禁 `sun.*`）· `NeedBraces` ·
    `EmptyBlock`（option=text）· `EqualsHashCode` · `UpperEll` · `ModifierOrder` · `ArrayTypeStyle` ·
    `OneTopLevelClass` · `OuterTypeFilename` · `FileTabCharacter` · `PackageDeclaration`；
  - 实测违规做**纯格式修正**（预计很少：基线已无星号导入、无 Tab）；
  - 反证：临时加一条 `import java.util.*;` 与一个无注释空块，确认 `verify` 变红后复原。
- **不做**：
  - **不接 PMD**（与 SpotBugs 的缺陷扫描重叠，且全套 `errorprone` 在存量代码上噪声大；单开一轮再议）；
  - **不启 Google / Sun 全量规则集**（会产出数百条现存代码噪声，门禁会被迫 suppress 空转）；
  - **不加 `LineLength`**（实测 8 行 >200 字符，全部是 `AccountCommands` 的复合校验注解与一条 SSE Swagger 注解——拆注解无收益，在规则文件注释里写明理由）；
  - 不扫测试源码、不改业务逻辑、不改 CI workflow、不引入 Suppressions 文件。

## 3. 改动点（文件级）

| 文件 | 动作 | 说明 |
|------|------|------|
| `backend/pom.xml` | 改 | 插件 3.6.0 + Checkstyle **10.26.1**（显式锁版本）+ `check` 绑 `verify` |
| `backend/config/checkstyle/checkstyle.xml` | 新增 | 上述 14 条规则 + 排除项注释 |
| `backend/src/main/java/**` | 视实测 | 纯格式修正（预计 ≤ 个位数） |
| `AGENTS.md` | 改 | `mvn verify` 注释补「Checkstyle 风格门禁」 |
| `docs/项目现有问题清单.md` / 台账 / 驾驶舱 / README | 改 | E-003 收敛 + 版本登记 |

## 4. 步骤

- [ ] 1. 写规则集文件（含注释说明为什么没有 LineLength / PMD）；
- [ ] 2. `pom.xml` 接线，首次联网拉插件与 Checkstyle（3.6.0 + 10.26.1）；
- [ ] 3. `mvn -o checkstyle:check` 看违规清单 → 能安全修的做纯格式修正；**修不动的规则从规则集移除并写注释**（不允许靠 suppress 让门禁空转）；
- [ ] 4. 反证：加 `import java.util.*;` / 空块 → `mvn -o verify` 必须失败；复原后复跑全绿；
- [ ] 5. `SPRING_IT=true mvn -o verify` 全绿（测试基线 737 不变）；
- [ ] 6. 文档同步（AGENTS / E-003 / 台账 / 驾驶舱 / README）+ 一致性脚本。

## 5. 验证

- `mvn -o verify` 全绿、`Tests run` 仍 **737**、SpotBugs 0、jacoco 门禁过；
- `target/checkstyle-result.xml` 生成且 `error` 计数为 0；
- **反证两支各自变红**（星号导入 / 空块）；
- `backend-ci` 无需改 workflow 即获得该门禁（推送后实跑为凭证）。

## 6. 回滚

移除 `pom.xml` 插件段与 `config/checkstyle/` 目录即可；若已提交，`git revert` 该提交。纯格式修正的 Java 改动随提交一起回退，不影响业务语义。

## 7. 风险

- **规则过严**：只选 14 条高信号规则，且以实测违规为准逐步收敛，不追求「一次上全」；
- **插件与 Checkstyle 14 的兼容性**：不追最新 14.3.0，显式锁 **10.26.1**（10.x API 稳定、插件 3.6.0 兼容面广），首次下载后实测；
- **构建时间**：310 个源文件预计 +5~10 秒，可接受；若超过 20 秒再评估缓存。

---

## 实施结果（2026-10-26）

**与原计划一致，无规则删减、无偏差**：

| 项 | 计划 | 实际 |
|----|------|------|
| 插件 / 引擎 | 3.6.0 + 10.26.1 | 一致（Maven 经 aliyun 镜像拉取成功） |
| 规则集 | 14 条高信号 | 一致，`config/checkstyle/checkstyle.xml` |
| 首跑违规 | 预计 ≤ 个位数 | **6 处真实 UnusedImports**（MarkdownPreviewController 2 / RosterImportApplicationService 2 / TagRepositoryImpl 1 / PostController 1），全部删除后 **0 违规** |
| 反证 | 星号导入 + 空块变红 | 临时探针文件触发 **2 条违规、exit 1**；删除探针后复绿（未使用 suppressions） |
| 全量验证 | `verify` 全绿、737 不变 | `SPRING_IT=true mvn -o verify` **737 / 0 failures / Skipped 0** + SpotBugs 0 + Checkstyle 0 + BUILD SUCCESS |
| 构建耗时 | +5~10s | 单跑 `checkstyle:check` **3.3s** |
| `LineLength` | 不加 | 未加，理由见规则文件注释（8 行 >200 字符均为复合校验注解） |
| PMD | 不接 | 未接，E-003 行已书面判定 |

⚠️ **边界如实**：`backend-ci` 的实跑证据需推送后由 GitHub Actions 给出（本机只证明 `mvn -o verify` 链路与绑定生效）。
