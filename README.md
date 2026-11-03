# Tsuzuki

A manga reader for Android and Windows that keeps both in step: the same library, the same categories, the same page.

- **Android** (`android/`): the phone app. MangaDex is built in, with follows, lists, recommendations and a Feed of new chapters. Extension sources work too, and their chapters can be merged into a MangaDex title. Glass navigation, covers that grow into their page, and a reader that remembers the exact page.
- **Windows** (`windows/`): a native WinUI 3 reader that is also the sync hub. Pair your phone over Wi-Fi with a QR code, and your library and reading progress move both ways. No separate server.

What is in each release: `CHANGELOG.md`.

Tsuzuki is not affiliated with MangaDex or with any extension source.

## Download

Releases are on the [Releases page](../../releases). Android builds are tagged `android-v*`; Windows builds are tagged `windows-v*`.

- **Android:** install `tsuzuki-arm64-v8a.apk` (almost every phone from the last few years), or `tsuzuki-universal.apk` for other CPUs. Android asks you to allow installs from your browser or file manager once. The app checks for new `android-v*` releases itself. If you used a Tsuzuki beta, 0.1.0 installs over it and keeps your library.
- **Windows 10 or 11 (64-bit):** run `Tsuzuki-<version>-windows-setup.exe`. The installer is not code-signed, so SmartScreen asks once: choose **More info**, then **Run anyway**. On first run, allow Tsuzuki on private networks when Windows asks, or the phone cannot reach it. The app installs updates itself on restart.

## Pairing

1. Windows: **Settings > Sync > Pair a phone**. A QR code appears.
2. Android: scan the QR code with the phone camera and confirm **Pair**. You can also scan it from **Settings > Backup & sync**, or type the address and key shown on the PC there.

Both devices must be on the same network, and Windows must treat that network as Private. Tailscale works too. The protocol is described in `docs/SYNC.md`.

## Building

| App | Requirements | Command (from its folder) |
|---|---|---|
| Android | JDK 17 or 21, Android SDK (`local.properties` or `ANDROID_HOME`) | `./gradlew :app:assembleRelease` |
| Windows | .NET 10 SDK, Windows SDK 10.0.26100 | `dotnet build src/Tsuzuki.App -c Release` |

Tests: `./gradlew :app:testDebugUnitTest :data:testDebugUnitTest` in `android/`. In `windows/`, build `tests/Tsuzuki.Core.Tests` with `dotnet build tests/Tsuzuki.Core.Tests -c Release` and run `tests/Tsuzuki.Core.Tests/bin/Release/net10.0/Tsuzuki.Core.Tests.exe` (`dotnet test` does not run the xUnit v3 suite on SDK 10).

Release signing for Android is read from a properties file passed with `-Ptsuzuki.signing=<path>` or `TSUZUKI_SIGNING`; without it, release builds are unsigned.

## Credits

The Android app is built on [Komikku](https://github.com/komikku-app/komikku), which carries the work of [Mihon](https://github.com/mihonapp/mihon), [TachiyomiSY](https://github.com/jobobby04/TachiyomiSY) and [Tachiyomi](https://github.com/tachiyomiorg/tachiyomi). Parts of the MangaDex integration are adapted from [Neko](https://github.com/nekomangaorg/Neko). See `NOTICE`.

## License

Apache License 2.0. See `LICENSE` and `NOTICE`.
