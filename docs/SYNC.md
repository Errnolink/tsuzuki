# Sync protocol

Tsuzuki for Android syncs with the Tsuzuki for Windows sync hub over the local network. The hub is the single source of truth: phones merge their changes into it and receive what changed elsewhere. There is no cloud service in between.

This document describes protocol version 2 as both apps implement it.

## Payload

Every request and response body is a Tachiyomi-family backup message (the `.tachibk` protobuf), optionally gzip-compressed. The schema lives in `windows/src/Tsuzuki.Core/Proto/backup.proto`; the Android source of truth is `android/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/`.

The hub stores the backup as rows, not as a file:

| Row kind | Identity |
|---|---|
| Manga | `source|url` (MangaDex: source `2499283573021220255`, url `/manga/{uuid}`) |
| Chapter | the chapter `url` within its manga |
| Category | its `uid` |
| Source, app preference, source preference, extension store, saved search | their natural key (source id, preference key, index URL, source and name) |
| Any top-level field the hub does not model | its field number; the raw bytes are kept and sent back unchanged |

Unknown fields inside modelled messages also survive a round trip through the hub.

## Pairing

The hub generates an API key and shows a QR code with a link:

```
tsuzuki://pair?host=<http://lan-address:port>&key=<api key>&name=<pc name>
```

Scanning it on the phone opens a pairing confirmation. The phone stores the host and key; nothing else is exchanged. Generating a new key on the PC unpairs every device.

## Authentication

Every `/api` request carries the key in the `X-API-Token` header. A wrong key returns `401`. Ten failures from one address within 60 seconds return `429` until the window passes.

Traffic is plain HTTP on the local network. Do not expose the hub port to the internet.

## Endpoints

| Method and path | Purpose |
|---|---|
| `GET /api/sync/v2/capabilities` | Returns `{version, merge, snapshot, seq, caps}`; `caps` lists optional features (below). |
| `GET /api/sync/v2/version?since=<seq>&wait=<s>` | Returns `{"seq": n}`. With `since` and `wait` (at most 30 s), the hub holds the request until its sequence passes `since` or the wait ends. |
| `POST /api/sync/v2/merge` | Merges the request backup and returns what the device is missing. |
| `GET /api/sync/v2/snapshot` | Returns the whole store; `304` when the `X-Sync-Cursor` header already equals the hub sequence; `404` before anything was stored. |
| `POST /api/sync/event` | JSON `{event, device_id, device_name}` status pings (`SYNC_STARTED`, `SYNC_SUCCESS`, `SYNC_FAILED`, `SYNC_ERROR`, `SYNC_CANCELLED`); returns `204`. |

### Merge request headers

| Header | Meaning |
|---|---|
| `X-Device-ID` | Required, at most 128 characters. Identifies the device; changes it made are not echoed back to it. |
| `X-Device-Name` | Display name shown in the PC's device list. |
| `X-Sync-Cursor` | The hub sequence the device last acknowledged; `0` for a first sync. |
| `X-Sync-Full` | `true` when the body is the device's whole library rather than a delta. |
| `X-Sync-Deleted-Categories` | Comma-separated category uids the device deleted. |
| `X-Sync-Caps` | Capabilities the device supports, from the list the hub advertised. |

### Merge response headers

| Header | Meaning |
|---|---|
| `X-Sync-Cursor` | The new hub sequence. The device stores it after applying the response. |
| `X-Sync-Changed` | `true` when the response contains anything the device should apply. |
| `X-Sync-Full-Requested` | `true` when the device's cursor was ahead of the hub (for example after the hub was reset). The device must send its whole library next time. |

## Sequence and cursors

Each merge that writes anything raises the hub sequence by one and stamps every written row with the new sequence and the writing device. A device's cursor is the sequence it last applied. On merge, the hub answers with every row stamped after the cursor, except rows that device wrote itself.

A cursor of `0`, or a cursor ahead of the hub, gets the full store back.

## Conflict rules

Manga, chapters and categories carry a `version` and a `last_modified_at`:

1. The higher `version` wins.
2. On equal versions with different content, the later `last_modified_at` wins.
3. If both are equal, the stored copy stays.

When the stored copy wins, the hub sends it back to the device in the same response, so the device converges in one round trip.

Sources, preferences, extension stores and saved searches have no version: the incoming copy replaces the stored one when its bytes differ.

Absence never deletes. A manga or chapter missing from a request stays on the hub. Only categories have deletes, carried in `X-Sync-Deleted-Categories`; a deleted category returns only if a later edit has a higher version.

A category that arrives with an unknown uid but the same name as a stored category is merged into the stored one, and manga that referenced it are remapped.

## Optional capabilities

| Capability | Effect |
|---|---|
| `chapter-delta` | The response carries only chapters that changed since the cursor, not every chapter of every changed manga. Category rows travel only when a category changed. |
| `version-wait` | The hub supports the long-poll form of `GET /api/sync/v2/version`. The Android app uses it while in the foreground, so a change on the PC reaches an open phone in about a second. |

A device only sends a capability in `X-Sync-Caps` that the hub listed in its `caps`.

## Device behaviour (Android)

- A sync starts on app open and resume (after a short delay), after local changes (coalesced), periodically in the background, and from the hub long-poll while the app is open.
- Before building a delta, the device asks `GET /api/sync/v2/version`. If the hub sequence equals its cursor and nothing changed locally, it skips the sync.
- Deltas carry manga whose row or chapters changed since the last acknowledged sync. A full sync runs at least once every 24 hours and whenever the hub requests one.
- The device applies the response, then stores the new cursor. A failed run keeps the old cursor and retries on the next trigger.
