# Gnome

**English** | [简体中文](README.zh-CN.md)

> [!NOTE]
> Gnome is a customized fork of [Moe Memos](https://github.com/mudkipme/MoeMemosAndroid), published under the same GPLv3 license. It is not affiliated with either the [✍️memos](https://github.com/usememos/memos) or the Moe Memos project.

Gnome is an app to help you capture thoughts and ideas, with a flomo-style, low-friction quick capture experience on top of everything Moe Memos offers.

You can use Gnome with either a self-hosted [✍️memos](https://github.com/usememos/memos) server or locally on your device (no server required).

**Note: Gnome currently supports Memos 0.21.0 and Memos 0.27.0 to 0.30.0. Memos updates may introduce breaking API changes.**

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

- [✍️memos](https://github.com/usememos/memos) — the open-source, self-hosted memo hub this client connects to.
- [Moe Memos](https://github.com/mudkipme/MoeMemosAndroid) — the original Android client by [@mudkipme](https://github.com/mudkipme). Gnome is a customized fork of it and would not exist without it.

## License

Gnome is licensed under [GPLv3](LICENSE), same as the upstream Moe Memos project.
