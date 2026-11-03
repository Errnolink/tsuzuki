# Tsuzuki for Windows

Read `../AGENTS.md` first. This folder is the Windows app: one WinUI 3 app that reads manga and hosts sync for the Android app.

## Architecture

| Project | Role |
|---|---|
| `src/Tsuzuki.Core` | Engine with no UI: the SQLite library (`Storage/`), native sources (`Sources/`, MangaDex first), the image cache (`Images/`), the built-in sync hub (`Sync/`) and backups (`Backup/`). |
| `src/Tsuzuki.App` | WinUI 3 app (Windows App SDK, unpackaged, self-contained): shell, design system, motion and screens. |
| `tests/Tsuzuki.Core.Tests` | xUnit v3 behaviour tests for Core. |

Contracts between the two projects are the interfaces in `src/Tsuzuki.Core/**/I*.cs` and `Library/LibraryModels.cs`.

## Build and test

- `dotnet build src/Tsuzuki.App -c Release`
- `dotnet test --solution Tsuzuki.slnx -c Release`
- The phone-backup round-trip test runs only when `TSUZUKI_PHONE_BACKUP` points at a real `.tachibk`; otherwise it is skipped.
- Installer: `dotnet publish src/Tsuzuki.App -c Release -r win-x64 --self-contained -o publish`, then `vpk pack` (Velopack). Tags are `windows-v<version>`.

## Rules

- Identity must match the Android app exactly (see `../AGENTS.md`).
- The UI thread never blocks on I/O, and never waits in a way that pumps messages: the app installs `Services/NonPumpingSyncContext.cs` for this. A pumping wait re-enters XAML and crashes the process.
- Cover and page decoding run off the UI thread.
- The sync hub's wire format is shared with Android. Change it only together with the Android side, in one pull request.
