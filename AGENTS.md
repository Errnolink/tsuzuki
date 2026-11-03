# Tsuzuki

Guide for anyone, human or agent, working in this repo. Read this first, then the `AGENTS.md` of the folder you work in.

## Layout

| Path | What |
|---|---|
| `android/` | The Android app. Gradle project based on Komikku. Rules in `android/AGENTS.md`. |
| `windows/` | The Windows app and sync hub. .NET 10 / WinUI 3 solution. Rules in `windows/AGENTS.md`. |
| `docs/` | Cross-app documents: the sync protocol both apps implement. |

The two apps build and test independently. The only contract between them is the sync protocol and the shared identity of titles and chapters (below). A change to that contract lands on one branch that is merged into both dev branches.

## Shared identity

- A manga is `source|url`. A chapter is its `url` within its manga.
- MangaDex English is source `2499283573021220255`; manga url `/manga/{uuid}`; chapter url `/chapter/{uuid}`.
- Backups use the Tachiyomi-family protobuf format (`.tachibk`). Field numbers follow the Android backup models in `android/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/`.

## House rules

- Write behaviour tests only. A test must fail when something a user would notice breaks. Do not write tests that pin constants, wiring, generated structure or source text.
- No code comments. License and attribution headers are the only exception.
- No compatibility shims, fallbacks for removed paths, or speculative abstractions. Remove obsolete code in the same change that replaces it.
- Prefer a library already in the project before adding one; prefer an established library to writing your own.
- One logical change per commit, with a short imperative message.
- Run the changed path and observe the result before calling a change done. Tests alone are not proof.

## Branches and releases

- `main` holds stable releases only. `android-dev` and `windows-dev` hold the test builds for each app. Work happens on short `feat/<name>` branches, squash-merged into the matching dev branch. A dev branch enters `main` through a real merge, not a squash.
- Versions are tagged per app on `main`: `android-v<version>` and `windows-v<version>`.
- Test builds are betas of the next stable: after `0.1.0` come `0.1.1-beta.1`, `0.1.1-beta.2`, then `0.1.1`. Beta tags (`android-v0.1.1-beta.N`) are GitHub pre-releases.
- The Android `versionCode` is one counter that increases across every beta and stable build.
- The Android update checker reads only `android-v*` releases; the Windows one reads `windows-v*`.
- Release notes say what changed for the user, in plain language.

## Local setup

Machine-specific notes (SDK paths, device serials, signing locations) go in `AGENTS.local.md` next to this file. It is ignored by git and never committed.
