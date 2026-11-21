# NextNotes

开源、离线可用的 Android 手写笔记应用，**AI 是学习助手而不是附加功能**：圈选一道题，或者直接提问，AI 会看到你笔记里的内容（选区或当前页面），帮你答疑、检查解题过程。

NextNotes 是 [xnotes](https://github.com/shardulvs/xnotes-android)（MIT）的分叉。低延迟手写、无限画布、PDF 批注这些核心能力都来自 xnotes，感谢原作者 Shardul Vikram Singh。

## 功能

**手写（来自 xnotes）**
- 低延迟压感书写，手掌防误触
- 无限画布 + 分页笔记，任意缩放都清晰
- PDF 导入与批注、插入空白空间、矢量 PDF 导出
- Markdown、LaTeX、表格、录音同步回放、20+ 纸张模板
- 笔记就是你选定文件夹里的普通文件，不锁在私有数据库里

**NextNotes 新增**
- **AI 助手面板**：在笔记旁边打开，提问时自动附带选区或当前页面的截图
  - 讲解、逐步检查解题过程、只给提示、完整解答、总结等快捷提问
  - 回答流式输出，公式用 LaTeX 渲染，可以查看模型的思考过程
- **任意 AI 服务**：OpenAI 兼容接口（OpenAI、DeepSeek、通义千问、OpenCode Go、本地 Ollama 等）和 Anthropic 接口，可以保存多套配置随时切换
- **密钥只留在本机**：API Key 用 Android 密钥库加密保存，不会上传，也不会进入备份
- **笔尖长按切换橡皮擦**：给收不到笔身按键的手写笔用（比如小米平板 5 的灵感触控笔）

## 安装

从 [Releases](https://github.com/copylee711/nextnotes/releases) 下载最新的 `NextNotes-vX.Y.Z.apk` 安装即可。包名是 `com.nextnotes`，可以和原版 xnotes 同时安装。

## 使用 AI

1. 打开一篇笔记，点左下角的 **AI** 按钮。
2. 第一次使用时，选一个预设（或自定义），填写服务地址、模型名称和 API Key，点"测试连接"确认可用。
3. 用套索圈选一道题后提问，或者直接提问让 AI 看当前页面。模型需要支持图片输入。

截图只会在你提问时发送给你选择的服务。

## 开发

- 构建环境和国内网络下的配置：[docs/dev-setup.md](docs/dev-setup.md)
- 规划和路线图：[docs/plan.md](docs/plan.md)
- 代码约定：[AGENTS.md](AGENTS.md)

```
gradlew assembleDebug
gradlew :ai:testDebugUnitTest :app:testDebugUnitTest
```

## 许可证

MIT，见 [LICENSE](LICENSE)。保留了 xnotes 的原始版权声明。
