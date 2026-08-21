# Gnome

**English** | [简体中文](README.zh-CN.md)

> [!NOTE]
> Gnome is built on [Moe Memos](https://github.com/mudkipme/MoeMemosAndroid) as its base and original inspiration, and is published under the same GPLv3 license. It is not affiliated with either the [✍️memos](https://github.com/usememos/memos) or the Moe Memos project.

Gnome is an app to help you capture thoughts and ideas, with a flomo-style, low-friction quick capture experience. It is developed against the latest stable [✍️memos](https://github.com/usememos/memos) server.

You can use Gnome with either a self-hosted [✍️memos](https://github.com/usememos/memos) server or locally on your device (no server required).

**Compatibility: Gnome tracks the latest stable Memos release (currently validated up to 0.30.x). Memos 0.21.0 and 0.27.0 – 0.30.0 are also supported; 0.22 – 0.26 are not.**

## Features

### Quick capture (new in Gnome)

- Jump straight into a new memo from anywhere via a Quick Settings tile in the notification shade
- Save shared text, images and webpages as memos through the system share sheet
- `#` hashtag autocomplete with existing tags while editing
- Tap any day on the activity heatmap to browse all memos of that date
- Memo cards show character count, created time and last edited time
- Refined stats, heatmap and drawer visuals

### From Moe Memos

- Write memos like tweeting to yourself
- Use the app locally on your device (with export) or sync with your own ✍️memos server
- Offline-first experience with automatic sync when you are back online
- Material You design with dynamic themes and themed icon
- Rich memo content: Markdown editor and renderer, images, non-image attachments, and to-do items
- Organize and find memos with tags, pinning, and search
- Home screen widget and share sheet integration
- View your memo activity with a progress graph
- Full privacy protection, no data collection

## Migrating from flomo

[migration/](migration/) ships a safe-by-default tool that imports a flomo HTML export into a Memos v0.30 server through the official API: dry-run preview first, resumable state, full post-import verification, and rollback of only what it created. See [migration/README.md](migration/README.md) for details.

## Installation

Download the signed APK from the [Releases](https://github.com/eonewg/Gnome/releases/latest) page (Android 8.0+), or build it yourself with Android Studio or:

```shell
./gradlew assembleRelease
```

Looking for the original app? Moe Memos is available on [F-Droid](https://f-droid.org/packages/me.mudkip.moememos/) and [Google Play](https://play.google.com/store/apps/details?id=me.mudkip.moememos).

## Development

Gnome is developed with Kotlin and Jetpack Compose. Contributions are appreciated.

## Acknowledgments

- [✍️memos](https://github.com/usememos/memos) — the open-source, self-hosted memo hub Gnome is built for and follows.
- [Moe Memos](https://github.com/mudkipme/MoeMemosAndroid) — the Android client by [@mudkipme](https://github.com/mudkipme), which serves as Gnome's codebase base and inspiration. Gnome would not exist without it.

## License

Gnome is licensed under [GPLv3](LICENSE), same as the upstream Moe Memos project.
