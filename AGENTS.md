# AGENTS.md

本仓库的开发与发布须知，供贡献者和 AI 编码助手阅读。首次接手请完整读一遍。

## 项目概况

- **Gnome** 以 [mudkipme/MoeMemosAndroid](https://github.com/mudkipme/MoeMemosAndroid) 为基座参考与灵感来源，GPLv3，对 memos 和 MoeMemos 保持致谢（见 README）。对外定位：**跟随 Memos 最新稳定版适配**，不锁定旧版本窗口。
- 远程仓库：`origin` = eonewg/Gnome（默认分支 `custom-memos`）；`upstream` = mudkipme/MoeMemosAndroid（仅用于同步上游，禁止 push）。
- 定制与架构重构需求见 `Gnome_Android_Refactor_Prompt.md`；架构与重构进度见 `docs/architecture.md`、`docs/refactor-plan.md`；flomo 迁移工具见 `migration/`。
- 服务端兼容目标：跟随 Memos 最新稳定版（当前适配至 0.30.x；另支持 0.21.0 与 0.27.0 – 0.30.0，0.22 – 0.26 不支持）。Memos 发新稳定版时优先跟进适配。

## 版本与发布

- 版本号基于上游版本加定制后缀，如 `2.1.0-gnome.2`；`versionCode` 单调递增。
- 发版流程：改 `app/build.gradle` 的 `versionCode`/`versionName` → commit 推送 → `git tag -a vX.Y.Z-gnome.N -m "..."` → `git push origin vX.Y.Z-gnome.N`。
- **tag 必须带 `v` 前缀**：CI 只在 `v` 前缀 tag 上触发，且首个步骤会用正则校验 `^v[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.]+)?$`。
- CI（`.github/workflows/build-signed-release-apk.yml`）自动完成签名构建、创建 Release、上传 APK（产物名 `Gnome-v<tag>.apk`），无需人工介入。
- 仅 `alpha`/`beta` 后缀会标记为 prerelease；`-gnome.N` 是正式版。

## 签名（重要）

- 发布密钥 `gnome-release.jks` 与 `keystore.properties` 仅存本地，已被 `.gitignore` 排除，另有异地备份。
- 本地签名构建：向 gradle 传入环境变量 `ANDROID_SIGNING_STORE_FILE` / `ANDROID_SIGNING_STORE_PASSWORD` / `ANDROID_SIGNING_KEY_ALIAS` / `ANDROID_SIGNING_KEY_PASSWORD`（alias 为 `gnome`）。
- CI 签名来自仓库 secrets（`ANDROID_KEYSTORE_BASE64` 等 4 个），与本地同一把密钥。
- **所有已发布版本必须用同一把密钥签名**，否则用户无法原地升级；密钥丢失同样等于断更。

## 构建与测试

- JDK 17；Android SDK 由 `ANDROID_HOME` 环境变量指定。
- 发布构建：`./gradlew :app:assembleRelease`；调试构建：`./gradlew :app:assembleDebug`。
- 单元测试：`./gradlew :app:testDebugUnitTest`。本仓库**没有** `testReleaseUnitTest` 任务，不要使用。

## 上游同步

- 定期 `git fetch upstream && git merge upstream/main`，获取安全修复与新版 Memos 兼容。
- 预期冲突集中在定制时大改的文件：`MemosHomePage.kt`、`MemosCard.kt`、`MemoInputPage.kt`、`Heatmap.kt`、`SideDrawer.kt` 等。

## 约定与红线

- `gh` CLI 在本仓库会优先解析 `upstream` 远程，操作自己的仓库必须显式 `--repo eonewg/Gnome`。
- 用户可见文案品牌统一用 **Gnome**（不是 Moe Memos）；应用内链接（官网/隐私/致谢/反馈）一律指向 eonewg/Gnome。
- 文档双语维护：`README.md`（英文）与 `README.zh-CN.md`（中文）需同步修改；隐私声明在 `PRIVACY.md`。
- 保持 GPLv3 许可证与 LICENSE 文件不动（GPL 分支的义务），发布 APK 时源码须随 tag 可得（当前做法满足）。
- **禁止提交个人数据与本地产物**：`migration/*.zip`、`migration/*.state.json`、`.spec-workflow/`、`artifacts/`、`*.jks`、`keystore.properties`、`__pycache__/` 均已在 `.gitignore`，新增类似文件时保持同样处理。
