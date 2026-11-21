# NextNotes 协作约定（给 AI 编码代理）

NextNotes 是 [xnotes-android](https://github.com/shardulvs/xnotes-android)（MIT）的长期分叉，目标是开源手写笔记 + 以 AI 为核心的学习助手。规划见 `docs/plan.md`，本机构建环境见 `docs/dev-setup.md`。

## 提交、推送与发布

- **由代理自行决定时机，不需要每次询问用户**（用户于 2026-11-21 明确授权）：
  - 完成一个可工作的改动单元（能构建、测试通过、关键路径验证过）就提交。
  - 提交后推送到 `origin`（`github.com/copylee711/nextnotes`，默认分支 `main`）。
  - 发布：到达一个对用户有意义的里程碑时，打 `vX.Y.Z` 标签并推送，由 GitHub Actions 构建并发布 Release。
    - 发布前更新 `app/build.gradle.kts` 的 `versionName`（要和标签一致，工作流会检查）和 `versionCode`（每次加一）。
    - 在 `docs/releases/vX.Y.Z.md` 写给用户看的更新说明（中文、简短），工作流会用它作为 Release 正文；应用里的更新弹窗显示的就是这段内容。
- **绝不推送到 `upstream`**（已设置 `git remote set-url --push upstream no_push`）；`upstream` 也不拉取标签（`remote.upstream.tagOpt=--no-tags`），因为上游的 v0.x 标签和我们的版本号冲突。
- 提交只署用户的名字，不加 `Co-Authored-By`、`Generated with` 之类的 AI 署名。
- 提交信息沿用上游风格：标题是祈使句、不加类型前缀、结尾不加句号；正文说明改了什么、为什么改，每行不超过 72 列。
- 不提交密钥：API Key 只在设备上加密保存；`local.properties`、`keystore.properties`、签名文件都已 gitignore。

## 代码组织

- 尽量少改上游文件，以便 `git merge upstream/master` 跟进上游。
  - 新功能放在新文件里：AI 逻辑在 `:ai` 模块（`com.nextnotes.ai`），界面放在 `app/src/main/java/com/nextnotes/`。
  - 必须改上游文件时，改动要小而集中，并在旁边加 `NextNotes:` 注释。
- Kotlin 命名空间保持 `com.xnotes`，applicationId 是 `com.nextnotes`。

## 测试

- 单元测试：`gradlew.bat :ai:testDebugUnitTest`、`gradlew.bat :app:testDebugUnitTest`
- 模拟器：AVD `test_avd`；用 `adb shell input stylus ...` 模拟触控笔。
- 真机：小米平板 5 Pro（USB 或无线 adb）。手写手感相关的改动要在真机上验证。
- 用户可见的文字用简体中文（`values-b+zh+Hans/strings.xml`），同时补上英文 `values/strings.xml`。
