# Tsuzuki for Windows

A manga reader for Windows that keeps the Tsuzuki Android app in step: same library, same categories, same page. One app; there is no separate server to install.

- **Library and reading.** MangaDex built in: popular, latest, search, chapters, single-page, two-page and long-strip reading, exact page progress.
- **Phone sync.** The app is also the sync hub. Pair your phone over Wi-Fi with the QR code; library, categories, read state and page progress move both ways. Titles from other sources show here with their progress and are read on the phone.
- **Backups.** One-click `.tachibk` backups that Mihon-family apps can restore. A backup runs once a day to the folder you choose; you set how many to keep, and Google Drive for desktop is used only if you turn it on.
- **Stays out of the way.** Closes to the tray and can start with Windows, so your phone can sync whenever the PC is on.

## Data

Everything lives in `%LOCALAPPDATA%\Tsuzuki Reader`: `library.db`, `settings.json`, `cache/`, `backups/` and `logs/`. Uninstalling leaves this folder in place.

Building, pairing and the license are covered in the repository's main `README.md`.
