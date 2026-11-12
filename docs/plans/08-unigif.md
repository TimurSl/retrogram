# Feature track — UniGif GIF provider (HTTP)

**Goal:** a setting that reroutes the GIF panel from Telegram's GIF bot (`@gif`, backed by KLIPY) to a
self-hosted **UniGif** server over its HTTP API, plus an "Add to UniGif" action that uploads a GIF file to that
server. Independent of the UI phases; it can be built any time after WP 0.5 (`RetroConfig` pattern exists).

Nothing in this file has been built or run. Line numbers are from the tree at commit `738213b8` and will drift.
Items marked **verify** were inferred from reading code and must be confirmed in the spike (WP U.0) before the
WPs that depend on them.

## Decisions (agreed with the owner)

| # | Decision |
| :--- | :--- |
| D1 | Two providers in one selector: **KLIPY (`@gif`)**, the default and today's behaviour, and **UniGif**. |
| D2 | UniGif is reached by **direct HTTP** from the client, not through an inline bot. (A bot may be added later as a third option; out of scope here.) |
| D3 | The server URL is **user-entered**; there is no default host. The API key is **one per user**, entered in settings. |
| D4 | While UniGif is selected, **"Add to UniGif" replaces "Save to GIFs"** everywhere "Save to GIFs" appears. |
| D5 | Add uploads **the file itself with no tags** (it lands in Unsorted on the server). No tag prompt. |
| D6 | The panel's first tab is the **UniGif library sorted by use** (`sort=used`). Telegram's saved GIFs are not shown in UniGif mode. They are not deleted; switching back to KLIPY shows them again. |
| D7 | The **emoji category tabs are hidden** in UniGif mode (the library is hashtag-based). |
| D8 | A **hashtag suggestion row** appears while typing in the GIF search field. |

Proposed, not yet confirmed by the owner (defaults used below; each is one line to change):

| # | Proposal |
| :--- | :--- |
| P1 | The second tab ("trending" slot) shows the library sorted by **newest** (`sort=new`), because "used" is already the first tab. Alternative: hide the tab. |
| P2 | "Delete" in the GIF preview menu on a library item calls `DELETE /gifs/{id}` (move to trash) and is shown only when the key has the `delete` scope. |
| P3 | The setting is **global**, not per Telegram account. |

## How the GIF panel works today (what we hook into)

| Fact | Where |
| :--- | :--- |
| There is no KLIPY code or key in the client. Search is `messages.getInlineBotResults` sent to the user named in `MessagesController.gifSearchBot` (default `"gif"`, overwritten from server config). | `MessagesController.java:579, 1725, 5859`; `EmojiView.java:9074` |
| The panel has two adapters of one class: `gifAdapter = new GifAdapter(context, true)` (recent documents, then a "Featured" header, then trending results) and `gifSearchAdapter = new GifAdapter(context)` (search and emoji-tab results). | `EmojiView.java:2085–2086`, class at `8747` |
| Both adapters hold results as `ArrayList<TLRPC.BotInlineResult>` and bind cells with `ContextLinkCell.setLink(result, bot, …)`; recents are `TLRPC.Document`s bound with `setGif`. | `EmojiView.java:8759, 8847–8851` |
| All network search goes through one method: `GifAdapter.search(query, offset, searchUser, isEmoji, cache)`; the response lands in `processResponse(…)`, which fills `results`, sets `nextSearchOffset` and `searchEndReached`. | `EmojiView.java:9018–9160` |
| Paging: `checkGifSearchFieldScroll` calls `search(lastSearchImageString, nextSearchOffset, …)` when the last visible item is within 5 of the end. | `EmojiView.java:5503–5512` |
| Results are cached in memory (`gifCache`) and on disk (`MessagesStorage.saveBotCache`) under the key `"gif_search_" + query + "_" + offset`. | `EmojiView.java:9048, 9107–9110` |
| `GifSearchPreloader` pre-fetches neighbouring emoji tabs with its own copy of the bot request. | `EmojiView.java:9161–9214` |
| Tabs are built in `updateGifTabs()`: recent (only if `recentGifs` is non-empty), trending (unless `NaConfig.disableFeaturedGifs`), then one tab per `MessagesController.gifSearchEmojies`. | `EmojiView.java:5838–5894` |
| The search field has its own emoji category strip (`categoriesListView`) with `recent` / `trending` / emoji categories. | `EmojiView.java:982–1047` |
| Grid cell size comes from `result.document` attributes, else `result.content.attributes`, else `result.thumb.attributes` (`TL_documentAttributeImageSize` or `TL_documentAttributeVideo`). | `EmojiView.java:9233–9303` |
| Tapping a result calls `delegate.onGifSelected(view, result, query, bot, …)`. In `ChatActivityEnterView` a `BotInlineResult` is sent with `params = {id, query_id, force_gif}` through `SendMessagesHelper.prepareSendingBotContextResult`. | `EmojiView.java:2097–2126`; `ChatActivityEnterView.java:13729–13750` |
| **`query_id` in the params makes the send a `messages.sendInlineBotResult`** (message type 9), i.e. Telegram's server sends the bot's result. A synthetic result has no valid `query_id`, so this path cannot be used as is. | `SendMessagesHelper.java:4828–5041, 6734` |
| Without `query_id`, a result of class `TL_botInlineResult` (not `TL_botInlineMediaResult`) with `type = "gif"` and a `content` URL is turned into a `TL_document` (`video/mp4` + `TL_documentAttributeAnimated` when the path ends in `mp4`) and sent with `path = cached file or the URL`. A path starting with `http` is downloaded by `ImageLoader.loadHttpFile(url, "gif", …)` and then uploaded. | `SendMessagesHelper.java:10501–10545, 10749–10766, 7058–7061`; `ImageLoader.java:3869` |
| After a successful send, the uploaded document is stored by `originalPath` (`putSentFile(originalPath, document, 2 or 1, …)`); `prepareSendingBotContextResult` sets `originalPath = result.content.url`. | `SendMessagesHelper.java:10701–10703, 9105–9118` |
| `ContextLinkCell` loads a GIF result from `inlineResult.content` / `thumb` **only when they are `TL_webDocument`**, through Telegram's web-file proxy (`WebFile`, needs a server-signed `access_hash`). A `TL_webDocumentNoProxy` content leaves `webDocument`, `webFile` and `urlLocation` all null, so nothing is drawn. | `ContextLinkCell.java:260–296, 327, 374–377` |
| "Save to GIFs" appears in: the message context menu (two branches) and its handler; the media viewer menu; the admin log; the GIF long-press preview. | `ChatActivity.java:50905, 50940, 36497–36504`; `PhotoViewer.java:5699–5713`; `ChannelAdminLogActivity.java:2280`; `ContentPreviewViewer.java:1103–1113, 1139–1142` |
| Other screens that search the GIF bot (not the main panel): attach-menu photo picker, story sticker sheet, story gallery. | `PhotoPickerActivity.java:1677–1714`; `EmojiBottomSheet.java:476–501`; `GalleryListView.java:1924–1951` |
| Fork settings: `RetroConfig` (Kotlin object of `ConfigItem`s in the `nkmrcfg` prefs) and `RetrogramSettingsActivity` (a `CellGroup` of `ConfigCell*` rows). String-valued keys with a dialog editor already exist (`ConfigCellTextDetail` + `showConfigDialog`, used for the LLM API key and URL). | `app/retrogram/RetroConfig.kt`; `tw/nekomimi/nekogram/settings/RetrogramSettingsActivity.java`; `NekoTranslatorSettingsActivity.java:144–174` |
| OkHttp 5.3.2 and Gson 2.13.2 are already dependencies. Cleartext HTTP is allowed app-wide (`usesCleartextTraffic="true"`). | `TMessagesProj/build.gradle:385, 412`; `AndroidManifest.xml:125` |

> Correction to what was said in chat earlier: sending a UniGif result does **not** work with the unchanged
> send path, because of `query_id` (row 8 above). WP U.6 handles it.

## UniGif API used

Base URL = the user-entered server URL without a trailing slash. Every call sends
`Authorization: Bearer <key>`. Media URLs (`/m/{id}/{variant}`) are public and are fetched **without** the header.
Relative URLs in responses are resolved against the base URL.

| Client action | Request | Scope | Notes |
| :--- | :--- | :--- | :--- |
| Test connection | `GET /me` | any | Show `name`, `scopes`; warn when `read` or `upload` is missing. |
| First tab (library by use) | `GET /gifs?sort=used&status=ready&limit=50&cursor=…` | read | |
| Second tab (newest, P1) | `GET /gifs?sort=new&status=ready&limit=50&cursor=…` | read | |
| Search | `GET /gifs?q=<text>&status=ready&limit=50&cursor=…` | read | `q` is passed as typed: words are AND-ed hashtags, the last word is a prefix, `-word` excludes. |
| Hashtag suggestions | `GET /tags?q=<last word>&sort=popular&limit=20` | read | Empty field: `GET /tags/suggest?limit=12` → `recent`. |
| Usage ping after send | `POST /gifs/{id}/use` | read | Fire-and-forget; errors are ignored. |
| Add to UniGif | `POST /gifs?source=telegram&source_id=<document id>&wait=0`, raw body, `Content-Type: video/mp4` (or `image/gif`) | upload | `200` + `duplicate: true` = already in library; `201` = created. `wait=0` so the call returns as soon as the bytes are stored. |
| Delete (P2) | `DELETE /gifs/{id}` | delete | Moves to the server's trash. |

Error model: non-2xx responses are `application/problem+json` with `code` and `detail`. The client maps `code` to a
string (table in WP U.2) and shows `detail` only in the "Test connection" result. `429` carries `Retry-After`.

Fields read from a `Gif`: `id`, `status`, `tags`, `width`, `height`, `duration_ms`, `media.mp4.{url,width,height,bytes}`,
`media.preview_mp4.url`, `media.poster.url`. Items with `media == null` or `media.mp4 == null` are skipped.

## New code layout

All new classes go in package `app.retrogram.unigif` under `TMessagesProj/src/main/java/app/retrogram/unigif/`
(Java, to match the files they are called from). Upstream files get only short guarded hooks
(`if (UnigifConfig.isActive()) …`), per the "hook, don't revert" convention in `README.md`.

| Class | Responsibility |
| :--- | :--- |
| `UnigifConfig` | Reads the three `RetroConfig` items; `isActive()` = provider is UniGif **and** URL and key are non-empty; `baseUrl()` normalised. |
| `UnigifClient` | OkHttp calls, JSON parsing (Gson), error mapping. No Android UI imports, so it can be unit-tested on the JVM. |
| `UnigifModels` | Plain data classes: `Gif`, `GifPage`, `Tag`, `Me`, `UnigifError`. |
| `UnigifResults` | Converts `Gif` → `TLRPC.TL_botInlineResult`; recognises a UniGif result (`id` prefix `ugf:`); builds the wrapper `messages_BotResults`. |
| `UnigifSender` | Send path for a UniGif result (WP U.6). |
| `UnigifUploader` | "Add to UniGif": resolve the file, download from Telegram if needed, upload, bulletin (WP U.7). |
| `UnigifTagSuggestView` | The horizontal hashtag chip row under the search field (WP U.5). |

## Work packages

### WP U.0 — Spike: rendering and sending a synthetic result (S)

Purpose: settle the two **verify** points before the rest is written. Throwaway code is fine.

1. Hard-code one public MP4 URL. Build a `TL_botInlineResult` as in WP U.3 and put it in `gifSearchAdapter.results`.
2. **Rendering.** Add the hook from WP U.4 step 5 to `ContextLinkCell` and check that the cell plays the MP4.
   - Option A (preferred): set `urlLocation = <preview URL>` and let the existing
     `ImageLocation.getForPath(urlLocation)` branch (`ContextLinkCell.java:376`) load it over HTTP.
     **verify** that an HTTP MP4 loaded this way is decoded as an animation and not as a still image.
   - Option B (fallback): download previews ourselves with OkHttp into
     `FileLoader.getDirectory(MEDIA_DIR_CACHE)/ugf_<id>_p.mp4` and pass the local path to `getForPath`; show `poster`
     until the file exists.
   - Option C (fallback): use `media.poster` only in the grid (static) and animate only in the long-press preview.
3. **Sending.** Call `prepareSendingBotContextResult` with params that contain **no `query_id`** and check that the
   message appears as a looping GIF in a normal chat and in a secret chat.
   **verify** `SendMessagesHelper` routes a document whose `path` starts with `http` to
   `delayedMessage.httpLocation` for this document type (seen for photos at `:5830`; the document branch must be read).
   - Fallback: `UnigifSender` downloads `media.mp4` to the cache directory itself and passes the local path.
4. **Long-press preview.** `ContentPreviewViewer.java:1949–1951` loads `WebFile.createWithWebDocument(content)`.
   `WebFile` marks `TL_webDocumentNoProxy` as `noproxy`; **verify** the preview plays, otherwise add a
   `getForPath(url)` branch there for UniGif results.

Acceptance: a written note in this file (replace this WP's text) saying which option was taken for rendering,
sending and preview, with the reason.

### WP U.1 — Settings and config (S)

Files: `app/retrogram/RetroConfig.kt`, `tw/nekomimi/nekogram/settings/RetrogramSettingsActivity.java`,
`res/values/strings_neko.xml`, `app/retrogram/unigif/UnigifConfig.java`, `src/test/java/app/retrogram/RetroConfigTest.java`.

1. `RetroConfig`: add three items. `load()` currently reads every item as a Boolean (`RetroConfig.kt` `load`), so
   either give it a typed reader or load the new items separately in `init()`; keep `RetroConfigTest` passing.

   | Key | Type | Default |
   | :--- | :--- | :--- |
   | `RetroGifProvider` | int | `0` (0 = KLIPY, 1 = UniGif) |
   | `RetroUnigifUrl` | string | `""` |
   | `RetroUnigifKey` | string | `""` |

2. `RetrogramSettingsActivity`: new section after the interface section.
   - Header "GIFs".
   - `ConfigCellSelectBox` "GIF provider": "KLIPY (@gif)", "UniGif".
   - `ConfigCellTextDetail` "Server URL" (dialog editor, hint `https://gifs.example.com`).
   - `ConfigCellTextDetail` "API key" with the value masked, as the LLM key rows do
     (`NekoTranslatorSettingsActivity.java:169`).
   - `ConfigCellText` "Test connection" → `UnigifClient.me()`; result in an alert: key name, scopes, a warning line
     per missing scope (`read`, `upload`; `delete` is optional), or the mapped error.
   - Info text under the section: what is rerouted, that Telegram's saved GIFs are hidden while UniGif is on, and
     that media is fetched from the entered server.
   - URL and key rows are shown only when the provider is UniGif (rebuild rows in `callBackSettingsChanged`).
3. URL normalisation in `UnigifConfig.baseUrl()`: trim, strip trailing `/`, prepend `https://` when no scheme.
   Show a one-line warning under the URL row when the scheme is `http://` (the key travels in clear text).
4. The new keys are live (no restart): extend the `callBackSettingsChanged` exclusion list so no restart tooltip
   is shown for them.
5. **Check** whether the fork's settings export/backup includes `nkmrcfg` string values. If it does, exclude
   `RetroUnigifKey` from the export.

Acceptance: values persist across restart; "Test connection" shows the key name with a valid key, a clear message
with a wrong key, wrong URL and no network; `RetroConfigTest` passes.

### WP U.2 — `UnigifClient` (M)

1. One shared `OkHttpClient` (connect 10 s, read 20 s, write 60 s for uploads). Reuse `ExteraHttpClient`'s
   builder settings if it has proxy handling worth keeping; **check** whether the app's Telegram proxy setting
   should apply to these calls (default: no, direct connection).
2. Methods (all asynchronous, callbacks on the UI thread via `AndroidUtilities.runOnUIThread`):
   - `me(Callback<Me>)`
   - `listGifs(String q, String sort, String cursor, Callback<GifPage>)` → returns an `okhttp3.Call` so the
     adapter can cancel it.
   - `listTags(String prefix, Callback<List<Tag>>)`, `suggestTags(Callback<List<Tag>>)`
   - `use(String id)`
   - `add(File file, String mime, String sourceId, Callback<Gif>)`
   - `delete(String id, Callback<Void>)`
3. The `Authorization` header is added by an interceptor **only** for requests whose host equals the configured
   base URL host and whose path does not start with `/m/` (so the key never goes to media or redirect targets).
   Disable following redirects to a different host for authenticated calls.
4. Error mapping (`UnigifError.code` → string resource):

   | Condition | Message |
   | :--- | :--- |
   | no URL or key | "UniGif is not set up" |
   | `unauthorized`, `invalid_credentials` | "UniGif key was rejected" |
   | `scope_required`, `forbidden` | "This UniGif key lacks the %s permission" |
   | `rate_limited` (429) | "UniGif rate limit reached, try again in %d s" (`Retry-After`) |
   | `too_large` (413) | "File is too large for UniGif" |
   | `unsupported_media`, `invalid_media` | "UniGif cannot process this file" |
   | IO exception, timeout | "Cannot reach the UniGif server" |
   | anything else | "UniGif error (%d)" |

5. After a 429, the client refuses further list/search calls until `Retry-After` has passed (returns the same
   error locally) so scrolling does not hammer the server.
6. Unit tests (`src/test/java/app/retrogram/unigif/UnigifClientTest.java`) against OkHttp `MockWebServer`
   (add `testImplementation 'com.squareup.okhttp3:mockwebserver3:5.3.2'`; **check** the exact artifact name for
   OkHttp 5): request line and headers per method, `Gif` parsing with null `media`, problem+json parsing, 429
   back-off, no `Authorization` on `/m/` URLs.

Acceptance: unit tests pass in CI.

### WP U.3 — Result mapping (S)

`UnigifResults.toInlineResult(Gif g, String baseUrl)` builds a **`TLRPC.TL_botInlineResult`** (the non-media class,
so the send path takes the `content` branch):

| Field | Value |
| :--- | :--- |
| `id` | `"ugf:" + g.id` |
| `type` | `"gif"` |
| `title` | `g.tags` joined with spaces (used for accessibility only) |
| `content` | `TL_webDocumentNoProxy`: `url` = absolute `media.mp4.url` + a `.mp4` suffix guard (see below), `mime_type = "video/mp4"`, `size = media.mp4.bytes`, attributes = one `TL_documentAttributeVideo` with `w`, `h`, `duration = duration_ms / 1000.0` |
| `thumb` | `TL_webDocumentNoProxy`: `url` = absolute `media.preview_mp4.url` (fallback `media.mp4.url`), `mime_type = "video/mp4"`, same attributes |
| `send_message` | `TL_botInlineMessageMediaAuto` with `message = ""` |
| `query_id` | `0` |

- The send path decides MP4 vs GIF with `finalPath.endsWith("mp4")` (`SendMessagesHelper.java:10529`) and derives
  the cache file extension from the URL. If the server's `/m/{id}/{variant}` URLs do not end in `.mp4`, append a
  harmless fragment or query (`#.mp4`) — **verify** against a real response and against `ImageLoader.getHttpUrlExtension`.
- `width`/`height` null → fall back to 100×100 (the layout manager's own default).
- Keep `poster` URL and the raw `Gif` reachable from the result: a side map `id → Gif` in `UnigifResults`
  (bounded, e.g. 500 entries) rather than a new field on the TL class.
- `UnigifResults.toBotResults(GifPage)` → `TL_messages_botResults` with `results`, `next_offset = next_cursor`
  (empty string when null), `cache_time = 0` (so nothing is written to Telegram's bot cache).
- `UnigifResults.isUnigif(BotInlineResult)` = `id != null && id.startsWith("ugf:")`; `gifId(result)` strips the prefix.

Unit tests for the mapper are possible only if the TL classes load on the JVM; **check** how `RetroConfigTest`
handles Android dependencies. If they do not load, keep the pure parts (URL resolution, extension guard) in a
helper that is tested and leave the TL assembly untested.

Acceptance: mapper tests pass (or the limitation is noted here).

### WP U.4 — Panel browse mode: tabs and the two library lists (M)

File: `org/telegram/ui/Components/EmojiView.java`; one hook in `org/telegram/ui/Cells/ContextLinkCell.java`.

1. **Mode snapshot.** `EmojiView` reads `UnigifConfig.isActive()` once into a field `unigifMode` when the GIF page
   is created (`:2085`), and re-reads it in the existing "panel shown" path (**find**: where `updateRecentGifs()` is
   called on open, `:5915` / `:6303`). When the value changes: clear both adapters' `results`, `resultsMap`,
   `gifCache`, call `updateGifTabs()` and reload.
2. **Recents off.** In `updateRecentGifs()` (`:6348`), when `unigifMode`, set `recentGifs` to an empty list instead of
   `MediaDataController.getRecentGifs()`. Everything that indexes `recentGifs` then sees zero items, and the
   "Featured" section header disappears on its own (`trendingSectionItem` is only set when `recentItemsCount > 0`,
   `:8880`).
3. **Tabs.** In `updateGifTabs()` (`:5838`), when `unigifMode`:
   - tab 0: "Most used" (reuse the recent icon `gifIcons[0]`), assigned to `gifTrendingTabNum` so the existing
     "trending tab → `gifAdapter`" logic applies;
   - tab 1: "Newest" (icon `gifIcons[1]`), stored in a new field `gifUnigifNewTabNum` (P1);
   - skip the emoji loop entirely (D7); `gifFirstEmojiTabNum` stays `-2`;
   - ignore `NaConfig.disableFeaturedGifs` in this mode (there is no "featured").
4. **Tab clicks.** In the tab delegate (`:2152–2183`): `gifUnigifNewTabNum` → `gifSearchAdapter.searchUnigifSort("new")`
   (new method: like `searchEmoji`, it sets `lastSearchIsEmoji = true` and a sentinel `lastSearchImageString = "\u0000new"`
   so paging and "same query → scroll to top" work unchanged). All `gifSearchEmojies.get(page - gifFirstEmojiTabNum)`
   call sites (`:2174`, `:8992`, `:1033`, `:2167`) must be guarded for `unigifMode`; an unguarded one throws
   `IndexOutOfBounds`.
5. **Data source.** At the top of `GifAdapter.search(query, offset, searchUser, isEmoji, cache)` (`:9018`), when
   `unigifMode`, branch to a new `searchUnigif(query, offset, isEmoji)` and return:
   - cancel the previous `okhttp3.Call` (new field next to `reqId`; set `reqId = 1` while a call is running so
     `checkGifSearchFieldScroll`'s `adapter.reqId == 0` guard keeps working, and reset it in the callback);
   - choose the request: empty query on `gifAdapter` → `sort=used`; sentinel `"\u0000new"` → `sort=new`; otherwise
     `q=query`;
   - on success call the existing `processResponse(query, offset, false, isEmoji, false, key, UnigifResults.toBotResults(page))`
     with `key = "ugf_" + query + "_" + offset` so nothing collides with the `@gif` cache; set `bot = null`;
   - on error: stop the progress indicators (`gifSearchField.showProgress(false)`), set `reqId = 0`, show the mapped
     message in the empty view (step 7); do **not** fall back to `@gif`.
6. **`loadTrendingGifs()`** (`:8933`): in `unigifMode` call `search("", "", false, true, false)` regardless of
   `disableFeaturedGifs`. **`GifSearchPreloader.preload`** (`:9172`): return immediately in `unigifMode`.
   **`searchBotUser()`** is never reached in this mode.
7. **Empty and error states.** `GifProgressEmptyView` (`:9310`) gets a `setMessage(CharSequence)` used for:
   "UniGif is not set up" (with the settings path), "No GIFs in your UniGif library yet", "Nothing found", and the
   error strings from WP U.2. `gifAdapter` (the `withRecent` one) has no empty view today (`progressEmptyView` is null
   for it, `:8787`); in `unigifMode` show the same view over the grid when its `results` is empty after a load.
8. **`ContextLinkCell` hook** (`:260–296`): when `UnigifResults.isUnigif(inlineResult)`, set
   `documentAttachType = DOCUMENT_ATTACH_TYPE_GIF` and the image source per the option chosen in WP U.0
   (A: `urlLocation = inlineResult.thumb.url`). Nothing else in the cell changes.
9. **Search-field category strip** (`:982–1047`): in `unigifMode` do not create `categoriesListView` for the GIF
   field (or keep it permanently hidden with `updateCategoriesShown(false, false)`); the suggestion row from WP U.5
   takes its place.
10. **Item clicks** (`:2097–2126`): `gifAdapter.bot` is null in this mode; pass `null` as parent. The receiving code
    casts `(TLRPC.User) parent` (`ChatActivityEnterView.java:13737`), which is safe for null, but WP U.6 intercepts
    before that line anyway.

Acceptance (device or emulator, against a real server):
- UniGif mode shows two tabs, no emoji tabs, the library by use on the first, newest on the second.
- Scrolling loads further pages until `next_cursor` is null; no duplicate rows.
- Switching the provider in settings and reopening the panel shows the other provider's content with no stale rows.
- With a wrong key / unreachable server the panel shows the message and does not spin forever.
- KLIPY mode is unchanged (compare with a build before the change: tabs, trending, emoji tabs, search, paging).

### WP U.5 — Search and hashtag suggestions (M)

Files: `EmojiView.java`, `app/retrogram/unigif/UnigifTagSuggestView.java`.

1. Text search already funnels into `GifAdapter.search(text, delay)` (`:8964`) → the hook from U.4 step 5. In
   `unigifMode` do not lower-case the query before sending (`lastSearchImageString = text.toLowerCase()` at `:8997`
   is only used as the "current query" token; keep it, but send `text.trim()`). **Check** with the owner whether
   tags are case-insensitive on the server; if so, lower-casing is harmless.
2. Clearing the field in `unigifMode` returns to the tab that was selected (first or second), using the guarded
   branch from U.4 step 4.
3. `UnigifTagSuggestView`: a horizontal `RecyclerListView` of chips (`#tag`, with `gif_count` as secondary text),
   height 36 dp, placed in the search box where `categoriesListView` sits (`:1047`).
   - Field empty and focused → `suggestTags()` → `recent`.
   - Typing → debounce 250 ms → `listTags(lastWord)` where `lastWord` is the text after the last space, without a
     leading `-`. Fewer than 1 character → hide the row.
   - Tap a chip → replace the last word with the tag plus a trailing space (a trailing space makes the server match
     it exactly), keep a leading `-` if the word had one, move the cursor to the end, run the search without delay.
   - Results are cached per prefix in memory for the panel's lifetime; stale responses (prefix no longer current)
     are dropped.
   - Hidden when there are no tags, on error (silently), and in KLIPY mode.
4. Search hint text in `unigifMode`: "Search hashtags" instead of the upstream GIF hint.

Acceptance: typing `ca` lists tags starting with `ca`; tapping `cat` then typing `fu` and tapping `funny` runs the
search `cat funny ` and shows GIFs carrying both; `-dog` excludes; clearing the field restores the tab content.

### WP U.6 — Sending a UniGif result (M)

Files: `ChatActivityEnterView.java` (one hook), `app/retrogram/unigif/UnigifSender.java`; possibly one hook each in
the other `onGifSelected` implementations (**find all**: `grep -n "onGifSelected" -r TMessagesProj/src/main/java`).

1. Hook at `ChatActivityEnterView.java:13729`, before the generic `BotInlineResult` branch:
   `if (UnigifResults.isUnigif(result)) { UnigifSender.send(…); return-equivalent; }`. Pass everything that branch
   has: dialog id, reply objects, quote, story item, notify, schedule date, send params, stars, mono-forum peer.
2. `UnigifSender.send`:
   1. Look up a previously uploaded Telegram document for this GIF:
      `MessagesStorage.getSentFile(contentUrl, isEncrypted ? SENT_FILE_TYPE_…_ENCRYPTED : SENT_FILE_TYPE_…)` on the
      storage queue. The store is filled by the existing code after each successful send
      (`SendMessagesHelper.java:9105–9118`, keyed by `originalPath` = the content URL). **verify** which type
      constant matches the `2` / `1` passed to `putSentFile` there.
   2. Hit → send that `TLRPC.Document` with `SendMessagesHelper.sendSticker(document, query, dialogId, …)` exactly
      as the "recent GIF" branch does (`ChatActivityEnterView.java:13722`). No upload.
   3. Miss → call `prepareSendingBotContextResult` with `params = {force_gif: "1"}` — **no `id`, no `query_id`** —
      so the document branch downloads the MP4 (or uses the local file, per WP U.0) and uploads it.
   4. In both cases, after handing the message to `SendMessagesHelper`, call `UnigifClient.use(gifId)`.
3. Do **not** call `MediaDataController.addRecentGif` / `MessagesController.saveGif` for UniGif sends (D6).
   Also guard the automatic "add sent GIF to recents" at `SendMessagesHelper.java:9232–9237` when
   `UnigifConfig.isActive()` — otherwise every UniGif send is silently saved to Telegram's GIF list.
   **Decide with the owner**: skip that auto-save for all GIF sends in UniGif mode, or only for UniGif-originated ones
   (default: all, consistent with D4).
4. Scheduled sends, "send without sound", captions ("Add a caption", action 11 in `ContentPreviewViewer`) and
   spoiler go through the same `delegate.sendGif` → `onGifSelected` entry, so they reach the hook. Test each.
5. Secret chats: the miss path produces a normal encrypted document upload; **verify** in the spike.
6. A cached document whose `file_reference` has expired fails with `FILE_REFERENCE_EXPIRED`. **Check** whether
   `sendSticker` already retries through `FileRefController` with the parent object; if not, on that error drop the
   cache row and resend through the miss path once.

Acceptance: first send of a GIF uploads and appears as a looping GIF; second send of the same GIF is instant (no
upload progress); works in a private chat, a group, a channel comment thread, a secret chat, scheduled, and with a
caption; the server's `sort=used` order changes after sends; Telegram's saved GIFs list does not grow.

### WP U.7 — "Add to UniGif" (M)

Files: `ChatActivity.java`, `PhotoViewer.java`, `ChannelAdminLogActivity.java`, `ContentPreviewViewer.java`,
`app/retrogram/unigif/UnigifUploader.java`, `strings_neko.xml`.

1. `UnigifUploader.add(int account, TLRPC.Document document, Object parentObject, BaseFragment fragment)`:
   1. Resolve the local file: `FileLoader.getInstance(account).getPathToAttach(document, true)`, also the
      message's `attachPath` when a `MessageObject` is available. Exists and length matches `document.size` → step 3.
   2. Otherwise start `FileLoader.loadFile(document, parentObject, PRIORITY_NORMAL, 0)` and wait for
      `NotificationCenter.fileLoaded` / `fileLoadFailed` for that file name (observer removed on completion and on a
      60 s timeout). Show a progress bulletin "Adding to UniGif…".
   3. `UnigifClient.add(file, document.mime_type, String.valueOf(document.id))`. Body is streamed from the file
      (`RequestBody` from `File`), not read into memory.
   4. Result bulletin: `201` → "Added to UniGif"; `200` with `duplicate` → "Already in your UniGif library";
      error → mapped message.
   5. Concurrent taps on the same document are collapsed (a set of in-flight document ids).
2. Call sites, each `if (UnigifConfig.isActive()) { label = "Add to UniGif"; action = UnigifUploader.add(…) } else { upstream code }`:

   | Place | Lines | Change |
   | :--- | :--- | :--- |
   | Message context menu items | `ChatActivity.java:50905, 50940` | Swap the label string; keep `OPTION_ADD_TO_GIFS` and the icon. |
   | Its handler | `ChatActivity.java:36497–36504` | In UniGif mode call the uploader and skip `saveGif`, the GIF hint and `addRecentGif`. |
   | Media viewer menu | `PhotoViewer.java:5699–5713` (+ where the `gallery_menu_savegif` item title is set — **find**) | Same swap, both the message and the page-block branch. |
   | Admin log | `ChannelAdminLogActivity.java:2280` (+ its menu label) | Same swap. |
   | GIF long-press preview | `ContentPreviewViewer.java:1103–1113, 1139–1142` | See step 3. |

3. `ContentPreviewViewer` GIF menu in UniGif mode:
   - `currentDocument != null` (a Telegram GIF previewed from an inline bot result or elsewhere): replace both the
     "Delete" (action 1) and "Save to GIFs" (action 2) entries with one "Add to UniGif" entry (new action constant).
   - `inlineResult` is a UniGif result (no document): no add entry; show "Delete" only per P2 (WP U.8).
4. Files that are GIFs by attribute but not MP4 (`image/gif` documents): upload with `Content-Type: image/gif`; the
   server converts.
5. Protected content: the menu items already sit behind the existing `noforwards` checks (`ChatActivity.java:50903`),
   so restricted chats do not offer the action. Do not add a path around that.

Acceptance: adding a GIF from a chat shows "Added to UniGif" and it appears on the "Newest" tab after reopening the
panel; adding it again shows "Already in your UniGif library"; a GIF that was never downloaded is downloaded and
then added; a key without the `upload` scope shows the permission message; KLIPY mode shows "Save to GIFs" and
behaves as before.

### WP U.8 — Delete from the library (S, proposal P2)

1. `UnigifConfig` caches the scopes from the last successful `GET /me` (refreshed by "Test connection" and once per
   process on first use).
2. `ContentPreviewViewer` (`:1103`): for a UniGif result with the `delete` scope cached, add "Delete" (red) →
   `UnigifClient.delete(id)` → on success remove the row from both adapters (`delegate.gifAddedOrDeleted()` currently
   only refreshes recents; add an `EmojiView` method that removes a result by id and notifies) and show
   "Moved to UniGif trash".
3. Without the scope the entry is not shown.

Acceptance: delete removes the tile immediately and the GIF is in the server's trash; a read-only key shows no entry.

### WP U.9 — The other three GIF search screens (M, second step)

Do this only after U.4–U.6 are accepted. Each screen has its own request code and its own result model.

| Screen | File | Result model | Approach |
| :--- | :--- | :--- | :--- |
| Attach menu → GIF search | `PhotoPickerActivity.java:1677–1714` | `MediaController.SearchImage` | Map `Gif` → `SearchImage` (`imageUrl` = MP4 URL, `thumbUrl` = poster, `type = 1`, size); the picker's own send path already handles URL-based search images (`SendMessagesHelper.java:11253`). **verify**. |
| Story sticker sheet → GIFs | `Stories/recorder/EmojiBottomSheet.java:476–501` | `BotInlineResult` | Reuse `UnigifResults`; the consumer downloads the file into the story editor — **read** how it resolves a result without `document`. |
| Story gallery search | `Stories/recorder/GalleryListView.java:1924–1951` | `BotInlineResult` → `MediaController.PhotoEntry` | Same as above. |

Until this WP lands, these screens keep using `@gif` in both modes; say so in the settings info text.

### WP U.10 — Strings, docs, checklist (S)

1. Strings in `res/values/strings_neko.xml` (English only; other locales fall back). Prefix `Unigif…`:
   provider row and options, URL, key, test connection and its results, info text, tab names, search hint, empty
   and error messages (WP U.2 table), "Add to UniGif", "Adding to UniGif…", "Added to UniGif",
   "Already in your UniGif library", "Moved to UniGif trash".
2. `docs/feature-matrix.md`: add a row for the feature. `docs/device-test-checklist.md`: add the acceptance lists
   from U.4–U.8.
3. `README.md` (repo root): one paragraph under features, including what data leaves the device (search text, the
   uploaded file and the Telegram document id go to the user's own server; nothing goes anywhere else).
4. Update the status column for this file in `docs/plans/README.md`.

## Order and dependencies

```
U.0 spike ─► U.1 settings ─► U.2 client ─► U.3 mapping ─► U.4 browse ─► U.5 search + tags
                                   │                          └──────► U.6 send ─► U.8 delete
                                   └────────────────────────► U.7 add
U.9 other screens (after U.4–U.6)        U.10 strings/docs (alongside each WP, closed last)
```

U.7 depends only on U.1 and U.2, so "Add to UniGif" can ship before the panel work if wanted.

## Risks and open points

| Risk / question | Handling |
| :--- | :--- |
| HTTP MP4 may not animate through `ImageLocation.getForPath(url)`. | WP U.0 options B/C. |
| The `http` path for document sends may not exist or may be flaky (it is legacy code). | WP U.0 fallback: `UnigifSender` downloads the file itself. |
| Media URL has no `.mp4` suffix, so the send path treats it as `image/gif`. | Extension guard in WP U.3; confirm with a real response. |
| `EmojiView` is a 10k-line upstream file that changes with every Telegram release; the hooks add merge cost. | Keep each hook to one `if (unigifMode)` line that calls into `app.retrogram.unigif`; list the hook lines in this file when done. |
| The API key is stored in plain app preferences, like the fork's other API keys. | Accepted for now; note it in the settings info text. Android Keystore-backed storage is a possible follow-up. |
| A user enters an `http://` URL. | Allowed (self-hosted LAN servers), with a visible warning (WP U.1 step 3). |
| Large library and `sort=used`: cursor stability while use counts change between pages. | Server concern; the client de-duplicates by id (`resultsMap`), so the worst case is a skipped item. |
| Uploading someone else's GIF to a personal server. | Same user intent as "Save to GIFs"; protected-content chats do not offer it (WP U.7 step 5). |
| No live test from CI: there is no UniGif server in the build environment. | Client logic is covered by `MockWebServer` tests; everything visual is on the device checklist. |
| P1–P3 are proposals. | Confirm with the owner before U.4 / U.8. |

## Out of scope

- UniGif through a Telegram inline bot (a possible third provider later).
- Tagging from the client (tag prompt on add, editing tags, the Unsorted queue).
- Browsing third-party providers through the server's `/search` endpoint and importing from them.
- Showing Telegram's saved GIFs and the UniGif library side by side.
- Per-account configuration, multiple servers.
- Server-sent events (`/events`) for live library updates; the panel reloads when it is opened.
