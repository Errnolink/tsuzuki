# Changelog

Versions are per app. Android releases are tagged `android-v<version>`, Windows releases `windows-v<version>`.

## 0.1.0 (Android and Windows)

First public release. Tsuzuki is a manga reader for Android with a Windows companion that keeps both in step: the same library, the same categories, the same page.

### Android

**MangaDex, built in**
- Sign in with your MangaDex account. Follows, reading status, custom lists, recommendations, author and group pages, and the comment thread for each title.
- Follows sync: titles you follow on MangaDex can come into your library, and reading status goes back.
- A Feed of new chapters from your library and your follows, grouped by day, with Up Next at the top.
- Details pages show the rating, follow count, chapter estimate and every available cover.

**Other sources**
- Extension sources from any extension store. Untrusted extensions ask before they run.
- Chapters from another source can be merged into a MangaDex title, so one entry covers every scanlation.
- Migration moves a title to another source and keeps your progress.

**Library and reading**
- Categories, filters, sorting, and a Continue button on every title.
- Reader with paged, long-strip and webtoon modes. Progress is saved to the exact page.
- Downloads, tracking (AniList, MyAnimeList, Kitsu and others), and backups in the `.tachibk` format that Mihon and its forks can restore.

**Look and feel**
- Covers grow into their details page and shrink back into the grid. The predictive back gesture previews where it goes.
- Glass navigation: the bottom bar shrinks to the current tab while you read down a list and comes back when you scroll up or tap it.
- Large titles, menus that grow out of the button that opened them, and lists that bounce at their edges with a long, soft glide.
- Browse home with shelves of popular and recently updated titles from each pinned source.
- Every screen, including migration, notes and source settings, uses the same Tsuzuki look.

**Sync with Windows**
- Pair once by scanning a QR code on the PC. Library, categories, read state and page progress then move both ways over Wi-Fi, within about a second while both apps are open.

**Updates and stability**
- The app tells you when a newer `android-v*` release is out and installs it.
- A damaged library entry is repaired at start-up instead of crashing the app, and Feed and Details keep working when one title has bad data.

### Windows

**Reading**
- Native Windows reader with Library, Details, Updates, History and Browse, using MangaDex directly.
- Single-page, two-page (right to left) and long-strip reading, with exact page progress.

**Sync hub for the phone**
- The Windows app is the sync hub. There is no separate server to install.
- Pair a phone from **Settings > Sync > Pair a phone**. Progress you make on either device reaches the other within about a second.
- It keeps syncing from the tray when you close the window, and can start with Windows.

**Backups**
- Backups that Android Tsuzuki and Mihon can restore, and restore from their backups.
- Choose the backup folder, choose how many backups to keep, and turn on Google Drive backups only if you want them. A backup runs once a day.

**Updates and comfort**
- Settings shows when a newer `windows-v*` release is out and installs it on restart.
- The window opens at the size and position you left it.

**First run**
- Windows asks whether Tsuzuki may use the network. Allow it on private networks, or the phone cannot reach the sync hub.
- The installer is not code-signed. SmartScreen asks once: choose **More info**, then **Run anyway**.
