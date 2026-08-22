# Gnome

[English](README.md) | **简体中文**

> [!NOTE]
> Gnome 以 [Moe Memos](https://github.com/mudkipme/MoeMemosAndroid) 为基座与灵感来源构建，遵循同样的 GPLv3 许可证开源。本项目与 [✍️memos](https://github.com/usememos/memos) 及 Moe Memos 项目没有任何隶属关系。

Gnome 是一款帮助你记录想法与灵感的 App，提供 flomo 风格的低摩擦速记体验，并跟随 [✍️memos](https://github.com/usememos/memos) 最新稳定版持续适配。

你可以连接自架设的 [✍️memos](https://github.com/usememos/memos) 服务器使用 Gnome，也可以完全在本地使用（无需服务器）。

**兼容性：Gnome 跟随 Memos 最新稳定版适配（当前适配至 0.30.x）。同时支持 Memos 0.21.0 与 0.27.0 – 0.30.0；0.22 – 0.26 不受支持。**

## 功能

### 极速速记（Gnome 新增）

- 在 Android 任意界面下拉通知栏，通过快捷设置磁贴极速进入新建 Memo 页面
- 通过系统分享把文字、图片、网页快速保存为 Memo
- 编辑时输入 `#` 自动列出并补全已有标签
- 点击活动热力图上的任意日期，查看当天的全部 Memo
- Memo 卡片显示字符数、创建时间和最后编辑时间
- 统计、热力图、侧边栏等界面细节打磨

### 继承自 Moe Memos

- 像发推一样给自己写 Memo
- 支持纯本地使用（可导出），或与自架设的 ✍️memos 服务器同步
- 离线优先，恢复联网后自动同步
- Material You 设计，动态主题与主题图标
- 丰富的内容形式：Markdown 编辑与渲染、图片、非图片附件、待办事项
- 通过标签、置顶和搜索整理、查找 Memo
- 主屏幕小组件与系统分享集成
- 活动热力图展示记录进度
- 完整隐私保护，不收集任何数据

## 从 flomo 迁移

[migration/](migration/) 提供一个默认只读预览的迁移工具，通过 Memos v0.30 官方 API 将 flomo HTML 导出包导入服务器：先预览再写入、支持断点续传、写入后全量校验、可只回滚本次创建的数据。详见 [migration/README.md](migration/README.md)。

## 安装

在 [Releases](https://github.com/eonewg/Gnome/releases/latest) 页面下载签名 APK（Android 8.0+），或使用 Android Studio / 以下命令自行构建：

```shell
./gradlew assembleRelease
```

> [!IMPORTANT]
> 自采用 `io.github.eonewg.gnome` 应用身份起，Gnome 将作为**全新应用**安装，与旧版本（`me.mudkip.moememos`）不构成原地升级，两者可并存。迁移方式：安装新版后重新登录你的 Memos 服务器，已同步到服务器的 Memo 会自动下载。**本地（无服务器）账号**的数据无法自动迁移，请先在旧版中使用「导出」功能保留数据的 ZIP 副本。

如果你要找的是原版应用：Moe Memos 可在 [F-Droid](https://f-droid.org/packages/me.mudkip.moememos/) 和 [Google Play](https://play.google.com/store/apps/details?id=me.mudkip.moememos) 下载。

## 开发

Gnome 使用 Kotlin 和 Jetpack Compose 开发，欢迎参与贡献。

## 致谢

- [✍️memos](https://github.com/usememos/memos) —— 开源、自托管的轻量级笔记服务，Gnome 所连接并持续跟随的服务端项目。
- [Moe Memos](https://github.com/mudkipme/MoeMemosAndroid) —— [@mudkipme](https://github.com/mudkipme) 开发的 Android Memos 客户端，是 Gnome 的基座参考与灵感来源。感谢原作者的出色工作。

## 许可证

Gnome 采用 [GPLv3](LICENSE) 许可证，与上游 Moe Memos 项目一致。
