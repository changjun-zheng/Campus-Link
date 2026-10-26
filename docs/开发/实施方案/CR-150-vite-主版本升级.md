# CR-150 · 前端 dev 构建链升级（Vite 5 → 7）

| 文档信息 | 内容 |
|---------|------|
| 版本 | v1.1 |
| 状态 | 已实施（2026-10-26，发起人选定「Vite 7.3.7 + CI Node 22」） |
| 维护人 | AI 会话（2026-10-26） |
| 最后更新 | 2026-10-26 |
| 关联 CR | [CR-150](../../变更日志/变更台账.md#cr-150) · 处置 [CR-148](../../变更日志/变更台账.md#cr-148) 的 dev 链残余 |

## 1. 目标

清零 CR-148 遗留的 dev 构建链漏洞：**vite 5.4.21（OSV 8 条，含 2 HIGH）· esbuild 0.21.5（1 条）· postcss-selector-parser 6.1.4（1 条）**。
三者只进开发 / 构建链，不进运行时产物（运行时自 CR-148 起已是零 CRITICAL / HIGH，本轮不动）。

## 2. 范围

- **做**：
  - `vite` `^5.4.8` → **`^7.3.7`**（OSV 实测 0 条）；`@vitejs/plugin-vue` `^5.1.4` → **`^6.0.10`**（peer 支持 vite 5~8、Vue 3.5）；
  - `overrides` 强制 **`postcss-selector-parser ^7.1.7`**（它由 `eslint-plugin-vue` 带入，升到 10.x 仍是 `^6.0.15`，只能 override；7.1.7 OSV 实测 0 条）；
  - 重新生成 `package-lock.json`；若 `unplugin-*` 在 Vite 7 下报错，同批升到兼容版并在「实施结果」记偏差；
  - **CI Node 20 → 22**（Vite 7 要求 `^20.19.0 || >=22.12.0`；`.github/workflows/frontend-ci.yml` 属 CI 红线，**需本次一并确认**）。
- **不做**：不升 Vite 8（默认换 Rolldown + lightningcss，变更面另开一轮）；不升 ESLint / TypeScript / Tailwind / Element Plus / Vue；不改业务代码与样式；构建命令名（`dev` / `build` / `preview` / `lint`）不变。

## 3. 改动点（文件级）

| 文件 | 动作 | 说明 |
|------|------|------|
| `frontend/package.json` | 改 | 三处版本 + `overrides` |
| `frontend/package-lock.json` | 重生成 | `npm install` 产物 |
| `.github/workflows/frontend-ci.yml` | 改 | `node-version: '20'` → `'22'`（红线，待确认） |
| `frontend/vite.config.ts` | 预期不改 | 已是 ESM + proxy 写法，Vite 7 仍支持；仅在报错时最小改动 |
| `docs/变更日志/变更台账.md` 等 | 改 | 实施结果 / 版本登记 |

## 4. 步骤

- [ ] 1. 改 `package.json`（vite / plugin-vue / overrides）→ `npm install`，确认锁文件只动预期包；
- [ ] 2. `npm run lint`（psp 7 的 override 能否被 eslint 正常加载，这是本方案最大不确定点）；
- [ ] 3. `npm run build`，记录产物与主 chunk（`index-*.js`）gzip 大小，与现状对比；
- [ ] 4. 起 dev（5173）+ 后端（8088），内置浏览器走**首页 / 帖子详情 / 发布页**，按来源过滤 `chrome-extension://` 后要求零控制台错误；
- [ ] 5. `npm run preview` 起产物，确认首页可访问、静态资源 200；
- [ ] 6. `python backend/scripts/scan_deps_osv.py --fail-on high` 复扫，确认三包清零、运行时仍零 CRITICAL / HIGH；
- [ ] 7. 改 CI Node 版本；同步台账 §1 实施结果、README 与驾驶舱；推送后以 `frontend-ci` 实跑为凭证（本机不能替代）。

## 5. 验证

- **依赖**：OSV 对 vite / esbuild / postcss-selector-parser 三包 0 条；`--fail-on high` exit 0；
- **构建**：`lint` exit 0、`build` 成功、无 CJS / Node API 弃用警告；
- **运行时**：dev 与 preview 两套栈下首页 / 详情 / 发布页实际渲染、零页面级控制台错误；
- **产物**：主 chunk gzip 体积变化如实记录（不设阈值，明显变大需说明原因）；
- **CI**：推送后 `frontend-ci` 绿（Node 22 + `npm ci` + lint + build）。

## 6. 回滚

- `git revert` 该提交（package.json + lock + CI 一行同进同退）→ `npm ci` 还原 `node_modules`；
- **B 计划**：若 Vite 7 与 `unplugin-*` 冲突且升级插件仍不通，退到 **vite 6.4.5**（OSV 亦为 0 条，Node 20 即可、CI 无需改），在方案文末记偏差。

## 7. 风险

- Vite 7 默认构建目标改为 `baseline-widely-available`，产物语法基线抬高（面向校内现代浏览器可接受，实施结果记录）；
- `overrides` 跨大版本（psp 6→7）给 Lint 链，若加载失败则改为「保留 6.x + 在问题清单登记为不可利用残留（输入仅本仓库 CSS）」；
- 本机 Node 24 满足要求；CI 的 Node 20 已临近 EOL，升 22 是顺带收益，但**属 CI 红线、需确认**。

---

## 实施结果（2026-10-26）

**与原计划一致，无偏差**：

| 项 | 计划 | 实际 |
|----|------|------|
| vite | ^7.3.7 | **7.3.7**（`npm ls` 实测） |
| @vitejs/plugin-vue | ^6.0.10 | **6.0.10** |
| esbuild（vite 传递依赖） | 由 vite 带入 | **0.28.2**（OSV 0 条） |
| postcss-selector-parser | overrides ^7.1.7 | **7.1.7**（`eslint-plugin-vue` 下的 `^6.0.15` 被 override 生效） |
| CI Node | 20 → 22 | `frontend-ci.yml` 已改为 `'22'` |
| `vite.config.ts` | 预期不改 | **未改**（ESM + proxy 写法 Vite 7 直接可用） |

**验证证据**：

- `npm run lint` exit 0；`npm run build` 成功（14.39s），主 chunk `index-*.js` **318.40 → 315.42 kB**（gzip **114.75 → 113.58 kB**），无 CJS / Node API 弃用警告。
- `scan_deps_osv.py --skip-maven --fail-on high`：前端 **319 个包零漏洞**、exit 0（后端 `pom.xml` 本轮未动；本机 shell 下扫描器直接调 `mvn` 报 `WinError 2`，故按 `--skip-maven` 跑，后端沿用 CR-148 的零 CRITICAL / HIGH 结果）。
- 真实浏览器（Vite 7 dev，5173 + 后端 8088）：首页 7 个版块入口、21 条帖子链接、无横向溢出；`/post/51` 渲染正常；`/post/41` 代码块 `class="language-java hljs"` + `hljs-number` span（高亮仍生效）；`/publish`（登录态）编辑器与标签区正常。三页控制台仅有 Vite 自身的 `[vite] connecting/connected` 两条 debug，零页面级错误。
- `vite preview`（4173）：`index.html` 200、入口 `index-BgeYXdIa.js` 200（preview 无 `/api` 代理，属预期，仅验静态产物）。
- CI 实跑待推送后由 `frontend-ci` 给出（本机不能替代）。
