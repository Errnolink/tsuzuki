# Changelog

From 0.2.0 on, one release covers both apps, tagged `v<version>`. 0.1.0 is tagged `v0.1.0`.

## 0.2.1 (Android and Windows)

### Android

#### Restoring a backup
- **The restore screen lists the extensions your backup needs.** It checks your extension repos and offers **Install** for each source it finds, or **Install all**. Sources none of your repos have are listed too, with a link to add a repo.
- **Neko merges link themselves.** A merged title whose source extension wasn't installed during the restore now links up as soon as you install that extension. You don't need to restore again, and chapter progress is kept.

#### Merging
- **Fixed: a merged title showed twice in the Library.** Merging left the original titles in your library next to the merged one. New merges hide them. Existing duplicates are cleaned up the next time the app starts.
- **Removing the second-to-last source from a merge unmerges the title,** and the source that remains becomes a normal library title again.
- **A clearer Merge sheet:**
  - Every source shows its cover and full title. One menu holds Move up, Move down and Remove.
  - Search results are grouped by source, and their covers load.
  - A title you've already merged says "Merged".
  - It searches all your other sources at once, skipping the ones already in the merge. Tap a source chip to search just that one.
  - Only the Merge button merges, so scrolling the results can't merge a title by accident.

#### Fixed
- **The top bar flickered when switching tabs.** Its buttons could swap back to the previous tab's, or vanish for a moment. They now change once, in step with the page.
- **Pull down anywhere on a title page to close it** when you're at the top. Before, only a pull on the header worked; on the chapter list the pull just stretched the list.


### Windows

- **Long-strip reading:** middle-click to auto-scroll, and set how far one mouse-wheel step scrolls in the reader settings.
- **The navigation pane no longer covers the reader.**

## 0.2.0 (Android and Windows)

### Android

#### Your library and sync were losing data. Fixed.
A full audit of backup, restore and sync found 12 bugs. All are fixed and each has a test.
- **Merged titles lost chapters after restoring a backup.** A merged title's sources were linked before the source titles were restored, so later sources were dropped. Every restore now links them once everything has arrived.
- **Restoring an older backup could move you back in a chapter.** The further page now always wins. Syncing never rolls back newer progress on your phone.
- **Backups wrote merged titles twice.** In one real library, 142 titles were doubled and 141 had every chapter doubled, so a merged title showed 168 unread instead of 84. New backups write each title once. Restoring an older backup that has the copies now skips them.
- **Every page turn told MangaDex you hadn't read the chapter,** and a library update erased your MangaDex read markers. Read state now goes to MangaDex only when it actually changes.
- **Chapters from a Neko-merged source were deleted** by the first MangaDex refresh if that source wasn't installed yet. They are now kept until you install it.
- **A sync asked for during another sync was dropped.** It now runs right after, up to three passes.
- **Rows that failed during a sync were marked done anyway.** They now trigger a full sync instead.
- **Restoring from a file left titles stuck as "syncing".** Cleared now.
- **Quiet series got a "next update" date in the past.** The prediction now always lands in the future.
- **Merged titles showed no cover.** MangaDex's image server blocks browser-like requests from other sources. Those covers now load.
- **Your library froze while a backup restored.** Open screens (Library, Feed, History, Updates) now wait and refresh once at the end, instead of re-running their queries for every restored title. Restoring a real 800-title library took 1 min 21 s with 0 errors.

#### Faster and smoother
- **Taps after going back were sometimes ignored, and Details sometimes loaded slowly.** A trace on a real phone found the app's main thread blocked for 1.1 to 1.5 seconds. Two causes, both fixed:
  - Opening a title waited for the database, and that wait couldn't time out while the database was busy.
  - Every title you opened re-saved its related titles, even ones already saved. Each save made History, Updates and the home-screen widget re-run their queries.
- **The library loads about twice as fast from a cold start** (measured on a 2,400-title library: grid ready in ~6.8 s instead of ~13.4–16.8 s). Two startup fixes: the home-screen widget no longer runs its library queries when no widget is placed, and its updates subscription no longer re-runs for every library change.
- **Opening a title starts moving within two frames.** It used to wait about 120 ms before starting.
- **Fewer dropped frames.** Measured on a phone: Details open and close drop to a 20 ms 90th percentile (from 23 ms), and scrolling the nav bar to 0.3 % janky frames (from 1.1 %).
- **Faster after installing.** The app ships a wider startup profile, so it runs at full speed sooner.
- **The Similar tab** shows covers again (every card was blank) and lists matches as soon as they're found, instead of after about 8 seconds.
- **Opening a chapter is no longer queued behind a library update.** MangaDex page-server lookups now go first.
- **Browse remembers lists** for 30 minutes, and searches for 10 minutes. You can browse offline, and pull to refresh or Retry fetches fresh.
- **Details re-checks a title for new chapters only if it was last checked more than 6 hours ago.** Before, library titles were never re-checked when you opened them.
- **Extension store lists are kept for 6 hours.**
- **Covers a site refuses are not requested again for 30 minutes.** Tap the cover on Details to retry right away.
- **Chapters downloaded by Neko are recognised** when you copy its downloads folder over, including MangaDex chapters (their folder names differ from ours).
- **The library badge settings apply to the new tiles:** unread pills, the Continue button and its reading-progress ring follow Library options › View Options, each grid size gets its own tile width, and the header pill shows the title count (and only opens the category menu if you use category tabs).

#### Kinder to sites
- **Page images:** at most 2 at a time per site, 350 ms apart. **Covers:** 4 at a time, 120 ms apart.
- **The reader loads 3 pages ahead** (was 10). A chapter it loads ahead of time fetches at most 2 pages until you reach it.
- **Library updates check 2 titles at a time** (was 5). **Downloads fetch 4 pages per chapter** (was 5).

#### Motion
- **Opening a title:** the cover grows into its page, timed to iOS and slowed so it reads as motion rather than a cut. The page behind blurs slightly.
- **Closing follows how you close.** A quick flick or swipe closes in about 150 ms; a slow, deliberate back takes about 380 ms, and speeds in between scale smoothly. The back button closes at the relaxed speed, unless you press it right after the page lands.
- **Drag a title page down to close it.** Pull down on the header: the page follows your finger and shrinks. Let go, and it flies home into its cover, or springs back if you didn't pull far. A fast flick needs only a short pull.
- **The reader opens out of what you tapped:** the Continue button, a chapter row, a tile's play button, an Up Next card, or a History, Updates or Feed row. It closes back into the same spot.
- **Every way of opening a title or chapter uses the same transition,** including Feed, History, Updates, MangaDex lists and the Similar tab.
- **Covers keep their own shape in flight.** A wide or square cover no longer stretches into a 2:3 card, and the animation lands without overshooting.
- **Menus grow out of the button that opened them.** Fixed across the app: several menus, such as the list settings like Tablet UI, used to open from the far left edge. The Library options menu now grows out of the filter button in the nav bar; it used to appear as an empty shape near the top of the screen and slide into place.
- **The nav bar shrinks without cutting tabs in half.** The minimized pill keeps its icon, and it reacts to your finger sooner.
- **The small title in the top bar appears only after the large title has scrolled away.** The band behind it stays solid, so rows no longer show through.

#### New
- **Title page header:** Settings › Appearance › Title page header lets you choose **Centred cover** (default) or **Classic**. The page holds its layout from the first frame, so nothing jumps when it finishes loading.
- **Cover colour behind title** (off by default): the title page takes a deep shade of its cover's colour, with matching buttons. Text keeps a readable contrast.
- **"N pages left in chapter"** in the reader's top bar.
- **Check for new chapters** is now a button in the title page's top bar. Pull to refresh was removed there because pulling down now closes the page.
- **Smart updates** (Settings › Library, off by default): background updates check only titles whose next chapter is predicted to be out. Late titles are re-checked every 12 hours; titles without a prediction, weekly. Pull to refresh still checks everything.
- **Release calendar** button on Feed.
- **Global search:** a source that failed shows its error and a Retry for just that source.
- **Merged titles:** the source line counts real sources and opens the Merge sheet. Chapters from several sources sort by number, then upload date, then source order. Tiles say "Merged".
- **Filters read All / Only / Hide** instead of strikethrough and warning red.
- **The update and sync progress chip** shows only on the home tabs, so it no longer covers Settings or Details.
- **Progress ring** (Library options › View Options, shown when the Continue reading button is on): turn off the ring around the tile's Continue button.
- **Navigation style "MangaDex" is now called "Simple"** (Library, Feed, Browse). **Show Updates / Show History** appear only for the Classic style, where those tabs exist.

#### A new Feed
- **Continue** at the top: the titles you're reading, as a row of covers in their real shape. The first one has a play button that opens your next page, with "page X of Y" and a progress bar. This replaces the old "Jump back in" card and the Up Next row.
- **New chapters on a timeline.** Each day has a marker with its title and chapter count and a Mark read action, and the marker stays pinned at the top while you scroll through that day.
- **Cards show the newest chapters as chips** (up to three, then "+N"). Tap a chip to open that chapter, or the card to open the title. Titles you've read stay in place, faded, so the list doesn't jump.
- **The Feed opens instantly after the first visit** and no longer reloads each time you switch to it.
- **Fixed: the app could run out of memory and close.** Every visit to Feed left a copy of its data behind, and each copy re-read the library on every change, so a library update could fill the app's memory.
- **Chapter numbers like 3.2 no longer show as "3.200000047683716"** on the Continue card, Feed and History.

#### One design
- **The Tsuzuki Next design replaces the old one.** Progress tiles with a reading ring, the large centred Details header, the new Feed and grouped header pills are now how the app looks. Settings › Advanced › Layout lab is gone: Material 3 standard motion timings and large scrolling titles are always on.

#### Updates
- **Beta builds and stable builds never pick up each other's APK** when updating.


### Windows

#### A new look
- **The Tsuzuki Next design replaces the old one.** It has:
  - a Mica window with a labelled navigation pane and a standard Windows title bar;
  - a new Home with a "Jump back in" hero;
  - progress tiles in the Library, plus a list view and bulk selection;
  - day-grouped History;
  - **a new Updates page**, matching the Android Feed: a Continue shelf of the titles you're reading (covers in their real shape, the first with a play button and "Page X of Y"), then new chapters on a timeline with a marker per day, its title and chapter counts, and Mark read. Cards show the newest chapters as chips (up to three, then "+N"). Click a chip to open that chapter;
  - a reader with a filmstrip (S), a thumbnail explorer (T) and a settings flyout.
- **Settings use Windows 11 cards:** Appearance, Library, Reader defaults, Sync, Backups, About.
- **Title page header:** **Centred cover** (default) or **Classic**, in Settings › Appearance. Covers keep their real shape.
- **Cover colour behind the header** (off by default), like on Android.
- **The small title in the title bar appears only after the large header has scrolled away,** on every page.
- **The Library filter menu gains Downloaded** (titles with a downloaded chapter; it refreshes as downloads finish) **and Merged**, next to Unread only and Readable on this PC.

#### Opening a title or chapter
- **One transition from everywhere:** the cover or row you clicked grows into the page and shrinks back into it. That includes the command palette, "Added to library" toasts, Downloads, Updates and History menus, Continue cards and the merge sheet.
- **Timed to iOS.** A title or chapter opens in about 210 ms, starting about 50 ms after the click. It closes in about 340 ms with a soft settle. The page behind blurs.
- **Drag a title page down to close it.** It follows the mouse; let go past a short distance, or flick, and it flies into its tile.
- **If Windows animations are off,** pages simply fade.

#### New
- **Mihon extensions on the PC.** Paste an extension store link in Browse › Extensions, then install extensions from it.
  - The first install downloads extension support once: about 227 MB, a Java runtime and Suwayomi's Android layer, checked against pinned checksums.
  - Extensions run in a separate process.
  - Sites behind a Cloudflare challenge, extension filters and extension settings aren't supported yet.
- **Downloads and offline reading:**
  - Download the next 5, 10 or 25 unread chapters, all unread, everything, single chapters or a selection.
  - The Downloads page has a queue with pause, reorder, retry and per-series delete.
  - Settings › Downloads sets the folder, unmetered-only, delete after reading, and auto-download by category.
  - Uses Mihon's folder layout, so chapter folders copied from a phone are picked up.
- **Tracking:**
  - Sign in to MangaUpdates or Kitsu (tokens stay in Windows Credential Manager), then link a title from Details › Tracking.
  - Finishing a chapter moves tracked progress forward, never back, and is retried if you were offline.
  - AniList, MyAnimeList and MangaDex lists advance locally and are sent by the phone.
- **Merge editing:** merge titles, choose the info source, reorder, add or remove sources, and unmerge, from Details. Merges sync to the phone the same way Android stores them.
- **Command palette (Ctrl+K):** titles, pages, commands and per-source search.
- **Browse filters:**
  - MangaDex: sort, rating, status, demographic, language, and tags to include or exclude.
  - Weeb Central: its own filters.
  - Search is scoped to the source you're in, with suggestions.
- **Weeb Central** as a built-in source. It requests at most once every 2 seconds.
- **Reader:**
  - layout, direction, fit and spread offset remembered per series;
  - spread offset (O), go to page (G, e.g. `12` or `12/48`), a shortcut sheet (?), Home and End, wheel and Ctrl+wheel zoom, double-click for 100 % or fit;
  - a redesigned end-of-chapter screen with an Up Next card and your resume point.
- **Updates and History:** dense rows on wide windows, an "Unread only" filter, and "Mark all in view as read" (it asks first).
- **Tile right-click menu:** Continue, Open details, Mark one ahead read, Set categories, Copy MangaDex link, Mark all read, Remove (it asks first).
- **Sync status in the title bar,** with clear states: Starting, Synced, Pair your phone, Sync off and Sync error. Banners explain problems such as a port already in use or a phone with the wrong key.

#### Faster
- **Home and Library are fully drawn about 2.5 to 3 seconds after launch** (was about 10). Covers are saved at display size, and on-screen covers load first.
- **History loads in about 2 ms** (was 347 ms). Library reads are about twice as fast, and chapter lists about three times.
- **A library refresh no longer redraws everything for each title.** It updates the screen in batches.
- **Browse lists are kept for 30 minutes** (searches for 10) and still show offline. Store indexes are kept for 6 hours. Details re-checks a title after 6 hours.
- **Kinder to sites:** at most 2 page requests per site at a time, 350 ms apart. The reader loads 3 pages ahead, plus 2 from the next chapter.

#### Fixed
- **A crash when reading double-page spreads.** The app was releasing images that Windows still needed to draw.
- **Adding several titles from Browse** duplicated a tile, shifted the grid and hid a title.
- **A library refresh** never deletes chapters you've read, started or bookmarked. A source returning an empty chapter list no longer wipes a title.
- **Chapters synced from the phone** (like 12.1) no longer get re-saved on every refresh.
- **Saving a title from Browse** no longer erases its details.
- **Back** returns to the tile you came from, and pages under the reader no longer show up in screen readers.
- **History** shows one header per day.
- **Hover actions on Updates and History rows** were invisible. They now fade in.
- **The tray icon comes back** after Explorer restarts. A failed automatic backup or update restart is reported instead of passing silently.
- **If Tsuzuki closed unexpectedly,** it says so on the next launch and points to the log folder.


### Both

- **Sync stays compatible with SyncYomi 1.5.8.**
- **Backups stay in the `.tachibk` format** that Mihon and its forks can restore.

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
