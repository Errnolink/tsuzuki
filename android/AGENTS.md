# Tsuzuki for Android

Read `../AGENTS.md` first. This folder is the Android app. It started from Komikku v1.14.1; Tsuzuki keeps Komikku's data, domain, source, merged-manga, backup, sync and reader-engine code and replaces the UI with its own design system and screens.

## Where code goes

- New code goes only in `dev.errnolink.tsuzuki.**`: screens and shell (`ui/`), MangaDex features (`mangadex/`), sync additions (`sync/`), the updater (`update/`), and the `designsystem` module. Do not start new UI under `eu/kanade/**`, `exh/**` or `mihon/**`.
- Inherited files (`eu/kanade/**`, `exh/**`, `mihon/**`, `tachiyomi/**`) change only at the hook points listed in `UPSTREAM.md`: one-call `Content()` delegations, import swaps, DI, build and manifest registration, and the listed required-logic files. A new hook gets its row in `UPSTREAM.md` in the same commit.
- Every redesigned inherited `Screen` or `Tab` is mapped in `app/src/main/java/dev/errnolink/tsuzuki/ui/Routes.kt`. A new upstream screen gets its `Routes.kt` row in the same change.
- MangaDex handlers live in `app/src/main/java/exh/md/`. Code adapted from Neko keeps Neko's file and function names, and its first line reads `// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.`

## Build and test

- JDK 17 or 21. Android SDK from `local.properties` (`sdk.dir=...`) or `ANDROID_HOME`.
- Unit tests: `./gradlew :app:testDebugUnitTest`.
- Release APKs: `./gradlew :app:assembleRelease`. Builds you hand to people for testing use `release` or `benchmark`, never `debug`: debug builds trace performance and run noticeably slower.
- Signing: pass `-Ptsuzuki.signing=<path to signing.properties>` or set `TSUZUKI_SIGNING`. Never commit signing files.

## Product rules

- Motion is part of the product. Transitions follow Material 3 motion (`designsystem/.../M3Motion.kt`); check them at normal animation speed, not only with animations off.
- Performance is a feature. Measure jank and p99 frame times before and after any change to lists, navigation or startup.
- Database work never runs on the main thread. Prefer one scoped query over subscribing to a whole table.
- Every release raises `versionCode`; tags are `android-v<versionName>`.
