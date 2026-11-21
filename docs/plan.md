# NextNotes 开发计划（v0.1 草案）

> 日期：2026-11-21 · 目标设备：小米平板 5 Pro + 小米灵感触控笔 · 开发方式：Claude Code
> 定位：**开源、离线可用的手写笔记 + 以 AI 为一等公民的学习助手**

---

## 1. 调研结论（已核实部分）

### 1.1 开源候选（2026-11-21 通过 GitHub API 和浅克隆源码核实）

| 项目 | 许可证 | Stars / 最近推送 | 规模与实现 | 是否有 AI | 结论 |
|---|---|---|---|---|---|
| **Xnotes** (shardulvs/xnotes-android) | MIT | 446 / 2026-11-19 | 约 10.9 万行 Kotlin；自研 OpenGL 渲染（`gl/GlWetPad.kt` 低延迟湿墨层）；内置 pdfium（C++/JNI）；无限画布带空间索引（`core/infinite/SpatialIndex.kt`）；分页、PDF 批注、Markdown/LaTeX 文本块、录音同步、模板 | **没有**，且 Manifest 中没有 INTERNET 权限（上游定位"不联网"） | **推荐作为底座 Fork** |
| pencil-note (longsiam-w) | MIT | 0 / 2026-11-08 | 约 4.7k 行 Kotlin，体量小 | 有：`ai/AiClient.kt`，OpenAI 兼容接口 + SSE 流式 + 选区图片 | 只借鉴 AI 交互思路，不作底座 |
| Folio (allient) | Apache-2.0 | 17 | 约 2.2 万行，KMP | 无 | 参考 |
| Saber / Butterfly | GPL-3.0 / AGPL-3.0 | 5054 / 2112 | Flutter | 无 | 只参考交互；许可证与 MIT 不兼容，**不复制代码** |

关键发现：

- Xnotes 已经有我们 AI 功能最需要的接口：`Editor.renderSelectionBitmap()` / `renderRegionBitmap()`（把套索选区或任意内容区域渲染成位图，最长边上限 4096px），`InfiniteEditor` 中也有对应实现。**"把当前页/圈选内容交给 AI"可以直接建在这上面。**
- Xnotes 的文本块已支持 commonmark + LaTeX 渲染，AI 回答可以作为可编辑的文本块插回画布，公式也能显示。
- 风险：`ui/Editor.kt` 约 1 万行、`canvas/InteractionController.kt` 约 5 千行，单文件很大；上游更新频繁。对策见 §3。
- 上游 CONTRIBUTING 明确不接受未经审查的 AI 代码，也很在意 UI 设计。AI 功能和上游"不联网"的定位冲突，**我们做的是长期分叉，不打算合并回上游**；只把通用 bug 修复按其规范回馈。

### 1.2 AI 接入

- **OpenAI 兼容协议**（`/v1/chat/completions` + `image_url` base64 + SSE）基本已成通用标准：阿里百炼 Qwen-VL（`dashscope.aliyuncs.com/compatible-mode/v1`）、SiliconFlow、OpenRouter 等聚合平台、本地 Ollama / llama.cpp server 都能用。一个适配器就能覆盖大多数国内外服务。
- **Anthropic Messages API** 单独做一个原生适配器（图片块、流式、提示缓存）。
- Kimi、GLM 的官方视觉接口文档本次没有查到，实现时再逐个核实。
- 本机已装 Ollama 和 llama.cpp，可在局域网跑本地视觉模型，适合离线和隐私场景。

### 1.3 协作

- 实时多人协作需要 CRDT。Yjs 的 Rust 版 yrs 有 C FFI，可以经 JNI 接入，但目前**没有成熟的官方 Kotlin/Android 绑定**，工程量大。
- Xnotes 把笔记存成用户选定文件夹里的普通文件，所以**多设备同步第一步不用写代码**：Syncthing / WebDAV 同步文件夹就能用。

### 1.4 本机环境（已检查）

- JDK 17（`D:\dev\jdk\jdk-17.0.19`，JAVA_HOME 已设置）✓
- Android SDK：platform 37、build-tools 36、AVD `test_avd`（Android 35 镜像）✓
- **缺**：NDK 27.0.12077973、CMake 3.22.1、platform 36（Xnotes 构建需要，用 sdkmanager 安装）
- adb 不在 PATH，位于 `%LOCALAPPDATA%\Android\Sdk\platform-tools`

---

## 2. 产品定位与功能优先级

按你的要求，**AI 与协作提到 P0/P1**。手写基础能力主要来自 Xnotes，不需要从零开发。

| 优先级 | 模块 | 内容 |
|---|---|---|
| P0 | 底座 | Fork Xnotes，改包名/应用名，能构建并装到模拟器和 Pad 5 Pro |
| **P0** | **AI 答疑（选区/当前页）** | 套索圈选或"当前视口"→ 预览将发送的图片 → 选择提问模板 → 侧边栏流式回答（Markdown + LaTeX） |
| **P0** | **AI 服务配置** | 多个服务配置（OpenAI 兼容 / Anthropic）、Base URL、模型、API Key 加密保存、连通性测试 |
| **P1** | **解题过程验证** | 按行编号后发送，AI 返回结构化结果（第几行对/错/存疑 + 原因），在画布上对应行旁边加可隐藏的批注标记 |
| **P1** | **AI 回写画布** | 回答一键插入为文本块；AI 批注放在独立的"AI 图层"，可整体显示或隐藏、清除，不改动你的笔迹 |
| **P1** | **会话随笔记保存** | 每个笔记有自己的 AI 对话历史，存为笔记旁的文件，可以导出 |
| P1 | 多设备同步 | 先写文档说明 Syncthing/WebDAV 文件夹同步；之后加内置 WebDAV |
| P2 | 实时伴学模式 | 开启后，停笔几秒自动把新增区域发给轻量模型检查，只在发现问题时提示（默认关闭，控制费用） |
| P2 | 手写识别 | 笔迹转文字/LaTeX，让 AI 收到图片 + 识别文本，提高准确率 |
| P2 | 苏格拉底模式 | 只给提示不给答案，用于自学 |
| P3 | 实时多人协作 | CRDT（yrs via JNI）+ 中继服务，多人同画布、AI 作为协作者之一 |
| P3 | 学习闭环 | 错题收集、闪卡生成、知识点总结 |

---

## 3. 架构：如何在 Xnotes 上加 AI 而不陷入合并地狱

```
xnotes (上游 Fork, :app)
 ├─ 少量钩子改动（尽量集中在少数位置）
 │    · 工具栏/选区菜单加 "问 AI" 入口
 │    · 暴露 CaptureProvider：当前视口 / 选区 → Bitmap + 选区内的文本与 LaTeX 块 + PDF 文本层
 │    · 暴露 AnnotationSink：在 AI 图层添加/清除批注
 └─ 依赖 ↓
:ai  (新 Gradle 模块，我们的代码都放这里)
 ├─ provider/   OpenAiCompatibleProvider, AnthropicProvider（OkHttp + SSE）
 ├─ context/    把截图、文本、行号整理成提示词；图片压缩与尺寸控制
 ├─ session/    会话模型、按笔记持久化
 ├─ verify/     解题验证：行切分 → 带编号图片 → 解析 JSON 结果
 ├─ secure/     API Key：Android Keystore + AES-GCM
 └─ ui/         Compose 侧边栏、设置页、发送预览
```

原则：

1. **上游代码改得越少越好**，改动集中在一个 `integration/` 包和少数调用点，便于定期 rebase 上游。
2. `:ai` 模块不依赖 Xnotes 内部类，只通过 `CaptureProvider` / `AnnotationSink` 两个接口交互，可以单独做单元测试。
3. 加 INTERNET 权限，但只在用户配置并主动触发 AI 时联网，不加任何统计或遥测。
4. 发送前能看到"将发送的内容"预览（图片 + 文本），隐私可控。

### 解题验证的关键设计

视觉模型给出的坐标不可靠，所以不让 AI 返回坐标：

1. 本地按笔画的纵向聚类把选区切成"行"，在发送的图片左侧画上行号 ①②③。
2. 要求模型按 JSON 返回：`[{line: 3, verdict: "error", reason: "...", fix: "..."}]`。
3. 本地根据行号找到对应笔画的包围盒，在旁边放批注标记；点击标记展开解释。

---

## 4. 分阶段里程碑

| 阶段 | 内容 | 验收 |
|---|---|---|
| M0 环境与底座（约 1 天） | 装 NDK/CMake/platform 36；Fork Xnotes 进本仓库（保留 MIT 版权声明，改 applicationId 避免和原版冲突）；模拟器构建运行；无线 adb 装到 Pad 5 Pro | 两台设备都能正常书写、打开 PDF |
| M0.5 真机基线 | 在 Pad 5 Pro 上测书写延迟、掌触、长笔记和大 PDF；记录问题 | 有基线记录 `docs/testing/device-baseline.md` |
| M1 AI 最小闭环 | `:ai` 模块 + 设置页 + 选区/视口截图 + 侧边栏流式问答 + LaTeX 渲染 | 圈一道题 → 问 AI → 看到带公式的流式回答 |
| M2 AI 协作 | 解题验证 + AI 图层批注 + 回答插入画布 + 会话持久化 | 写一段推导，AI 标出错误行，重开笔记历史还在 |
| M3 同步 | Syncthing/WebDAV 说明 → 内置 WebDAV | 平板和电脑之间笔记同步 |
| M4 进阶 | 伴学模式、手写识别、苏格拉底模式 | — |
| M5 实时协作 | CRDT 技术预研 → 原型 | 两台设备同画布实时可见 |

测试分工：**模拟器**用于 UI、AI 流程、单元/仪器测试；**Pad 5 Pro（无线 adb）**用于手写手感、延迟、掌触和性能。

---

## 5. 已确认的决定（2026-11-21）

- 底座：**Fork Xnotes**。上游以 `upstream` 远程并入完整历史（`git merge --allow-unrelated-histories`），以后用 `git fetch upstream && git merge upstream/master` 跟进。
- 应用名 **NextNotes**，applicationId `com.nextnotes`（debug 版 `com.nextnotes.debug`）。Kotlin 命名空间保持 `com.xnotes`，减少与上游的差异。
- AI 测试服务（已实测都支持图片输入，能看懂"17×23=381"并指出错误）：
  - OpenCode Go：`https://opencode.ai/zen/go/v1`，模型 `deepseek-v4.1-flash`，密钥在环境变量 `OPENCODE_GO_API_KEY`。**必须带 `x-opencode-session`（每个会话一个固定 ID）和能标识客户端的 User-Agent。** 注意：官方说明 Go 是为编程代理设计的，流量会受滥用监控，笔记应用调用存在账号风险。
  - DeepSeek 官方：`https://api.deepseek.com`，模型 `deepseek-flash` / `deepseek-v4-pro`（`deepseek-chat` 别名仍可用），密钥在环境变量 `DEEPSEEK_API_KEY`。
- 密钥不写进代码或仓库；开发期通过设置页手动填写，或用 debug 构建从本机 `local.properties` 注入（该文件已被 gitignore）。

## 6. 仍待确认

1. **"协作"指什么**：(a) 你和 AI 协作（AI 在画布上批注、回写）；(b) 多台自己的设备同步；(c) 和同学实时同画布。计划目前按 a > b > c 排序。
（已定：AI 服务完全由用户自定义——协议类型 / Base URL / 模型 / 密钥，可保存多套配置切换；OpenAI、DeepSeek、OpenCode Go 等只作为预填 URL 的模板，不设默认服务。）
