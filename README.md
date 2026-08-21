# Gnome

[![justforfunnoreally.dev badge](https://img.shields.io/badge/justforfunnoreally-dev-9ff)](https://justforfunnoreally.dev)

**Gnome** 是 [Moe Memos](https://github.com/mudkipme/MoeMemosAndroid) 的个人定制版，在原项目基础上增加了 flomo 风格的低摩擦速记体验。

**Gnome** is a customized fork of [Moe Memos](https://github.com/mudkipme/MoeMemosAndroid), adding a flomo-style, low-friction quick capture experience on top of the original app.

An app to help you capture thoughts and ideas. You can use it with either a self-hosted [✍️memos](https://github.com/usememos/memos) server or locally on your device (no server required).

**Note: Current version supports Memos 0.21.0 and Memos 0.27.0 to 0.30.0. Memos update may introduce breaking API changes.**

## Custom features / 定制功能

相比上游 Moe Memos，本定制版主要增加了：

- 在 Android 任意界面通过下拉通知栏的快捷设置磁贴（Quick Settings Tile）极速进入新建 Memo 页面
- 通过系统分享（Share Sheet）直接把文字、图片、网页快速保存为 Memo
- 编辑时输入 `#` 自动列出并补全已有标签
- 点击统计热力图上的任意日期，查看当天的全部 Memo
- Memo 卡片显示字符数、创建时间和最后编辑时间
- 统计、热力图、侧边栏等界面细节打磨，多语言翻译完善

In English:

- Jump straight into a new memo from anywhere via a Quick Settings tile
- Save shared text, images and webpages as memos through the system share sheet
- `#` hashtag autocomplete with existing tags while editing
- Tap any day on the heatmap to browse all memos of that date
- Memo cards show character count, created time and last edited time
- Refined stats/heatmap/drawer UI and improved translations

## flomo migration / flomo 迁移

[migration/](migration/) 提供一个默认只读预览的 flomo 导出迁移工具：通过 Memos v0.30 官方 API 导入 flomo HTML 导出包，支持断点续传、全量校验和只回滚本次创建的数据。详见 [migration/README.md](migration/README.md)。

## Installation / 安装

本定制版请从源码自行构建 APK（Android Studio 或 `./gradlew assembleRelease`）。

原版 Moe Memos 可通过以下渠道安装：

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/me.mudkip.moememos/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=me.mudkip.moememos)

## Features

- Write memos like tweeting to yourself
- Use it locally on your device (with export) or sync with your own ✍️memos server
- Offline-first experience with automatic sync when you are back online
- Material You design with dynamic themes and themed icon
- Rich memo content: Markdown editor and renderer, images, non-image attachments, and to-do items
- Organize and find memos with tags, pinning, and search
- Home screen widget and share sheet integration (text, images, and webpages)
- View your memo activity with a progress graph
- Full privacy protection, no data collection

## Development

The Android version is developed with Kotlin and Jetpack Compose. Any contributions are greatly appreciated.

## Acknowledgments / 致谢

- [✍️memos](https://github.com/usememos/memos) —— 开源、自托管的轻量级笔记服务，本客户端所连接的服务端项目。感谢其开源贡献。
- [Moe Memos (MoeMemosAndroid)](https://github.com/mudkipme/MoeMemosAndroid) —— 由 [@mudkipme](https://github.com/mudkipme) 开发的原版 Android 客户端。本项目基于它定制而来，感谢原作者的出色工作。

- [✍️memos](https://github.com/usememos/memos) — the open-source, self-hosted memo hub this client connects to.
- [Moe Memos](https://github.com/mudkipme/MoeMemosAndroid) — the original Android client by [@mudkipme](https://github.com/mudkipme); this project is a customized fork of it and would not exist without it.

This project is not affiliated with either project.

## License

This project is licensed under [GPLv3](LICENSE), same as the upstream project.
