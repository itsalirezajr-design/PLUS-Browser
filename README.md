# PLUS Browser

Native Kotlin / Jetpack Compose Android WebView browser. Package `com.plus.browser`; version 1.0, versionCode 8; Android 8.0+ (API 26). Launcher identity is unchanged.

## Build

Open the existing project in Android Studio, install SDK Platform 35 and use JDK 17. Set `sdk.dir` in `local.properties` to your SDK location, or let Android Studio create it, then run:

```
./gradlew :app:assembleDebug --no-daemon --console=plain
```

The output is `app/build/outputs/apk/debug/app-debug.apk`. The debug build is configured to use the persistent key at `signing/plus-debug.keystore` (alias `plusdebug`, debug-only password `android`). This newly generated key does NOT match the original APK. You may need to uninstall the old app before installing this build; uninstalling deletes its app data and app-specific downloads. Keep a private backup of the key; it is included in this project archive for reproducible debug builds. Do not use this debug key for production or share it publicly. Android Gradle Plugin 8.5.2 warns that compile SDK 35 exceeds its tested SDK 34.

## Persistence and downloads

Existing bookmarks, history and settings remain in their original shared preferences. Normal tab order, current URLs, titles and the selected normal tab are persisted as a session snapshot; private tabs are excluded. WebView's internal back/forward stack, form state and scroll position are not restored.

PLUS downloads are shown in the browser's menu. SQLite stores download metadata; WorkManager runs HTTP(S) downloads in the background, with a foreground service where supported. Partial files resume via HTTP Range and a strong ETag or Last-Modified validator, otherwise they restart. Download files are app-specific; they will be removed if the app is uninstalled. A download started from a private tab still appears in the Downloads list and can persist as a file and download record. URLs and cookies needed for unfinished authenticated transfers are stored in the app-private database. Servers, redirects, OS background restrictions and content types can affect downloads; blob URLs are not supported.

## Privacy and testing limitations

Private tabs do not add entries to PLUS history or restore as tabs, but Android WebView shares cookies and web storage with normal tabs. They do not hide activity from sites or the network. Use Clear cookies and site data to delete shared WebView data. Do not rely on this mode for confidential browsing.

File-upload chooser and camera/microphone/geolocation permission flows remain unsupported. Persian labels follow the device locale; some messages and accessibility descriptions remain English. The build and APK signature/metadata have been verified, but no device was attached and the software emulator did not finish booting for installation or behavioral testing. This is a debug build, not a production security-reviewed browser.

## Performance update

Opened tabs keep their WebViews attached when switching tabs or browser panels; inactive tabs are paused and destroyed when closed. Progress occupies a fixed-height slot. IME insets and adjustResize are configured, and the WebView and startup window backgrounds match the browser theme. Downloads UI database operations are dispatched to IO, and worker database checks are batched with progress writes.

Visual smoothness, scrolling, IME behavior and the ten-search scenario require on-device testing. A headless API 28 emulator was attempted in the build environment without KVM, but did not complete boot with only 2 GB of host RAM. No claim of device validation is made. Sites control their own page colors and may display white backgrounds.

## PLUS PRO+ (partial implementation)

PLUS PRO+ is free. The implemented tools are accessible when Android reports PLUS Browser as the default browser. The app checks the browser role at startup and on resume; the system confirmation screen requires explicit user action. Android 8–9 uses the default web-intent handler instead. Debug builds also include developer test access; release code is in a separate source set and does not contain the code or a code-matching implementation. The debug override can be revoked. The system role flow has not been tested on a device.

The scanner inspects exposed DOM elements and performance resource entries in the current WebView and groups several common multipart naming patterns. Results are unverified candidates, not proof of accessibility; scripts, protected resources, blob links, cross-origin frames and hidden resources may not be available. Download records use the existing internal WorkManager/SQLite implementation, with an adjustable transfer limit, duplicate URL prevention and ETA when possible. The existing transfer verifies advertised content length but does not verify a trusted checksum or recover corrupted files. Resource timing byte counts are not necessarily final file sizes.

Six selectable palettes are included; custom image backgrounds are not implemented. PLUS AI, PLUS Vault and PLUS Deep Freeze are not implemented and are visibly marked unavailable: no model, biometric protection, encrypted storage or safe tab hibernation is provided. Do not use this build as a vault. Private tabs continue to share WebView cookies and site storage. Downloads from private tabs remain in the download list. PLUS PRO+ labels are currently English.

Host checks: `./gradlew :app:testDebugUnitTest` exercises multipart naming, debug-code comparison and default-access policy. `python3 tools/test_scanner.py` exercises the scanner JavaScript against a synthetic DOM. A release APK was built solely to inspect code separation; the code string was absent and the release verification stub returns false. None of these checks establishes Android UI, lifecycle, WebView, biometric, download or installation behavior. No Android device was attached; the earlier software-emulator attempt could not complete boot without KVM under the workspace memory limit.

## Brand and navigation update

The toolbar and overflow menu use the transparent, vector P+ master mark in `res/drawable/plus_mark.xml`. The launcher icon remains unchanged. The home screen includes a PRO+ card; the overflow menu has a custom branded layout. The screen overlay no longer consumes all pointer movement, and the PRO+ list is explicitly given the space remaining below its header.

These layout and interaction changes have compiled but have not been visually or interactively tested on Android: no device or usable emulator was available. The earlier limitations for PLUS AI, Vault, Deep Freeze and private browsing remain.

## Fullscreen, search and toolbar update

The WebChromeClient handles fullscreen custom views in a window-level host and provides back-button, orientation and system-bar restoration logic. Device-level YouTube and HTML5 playback have not been tested.

Text entered into the main address bar is sent directly to the selected search provider when it is not a URL. The provider supplies the results in the existing WebView. Persian and mixed-language queries are UTF-8 encoded. Availability and result behavior depend on the provider and network.

The compact browser toolbar is 60 dp plus the reserved 3 dp progress slot, excluding system insets. Fullscreen, device screenshots, actual provider search interactions, result selection and RTL layout have not been verified on a device.

## Settings center update (debug build 9)

This is a **partial** Settings upgrade. Theme mode, accent, search engine, homepage,
partial English/Persian language choice, third-party cookies, normal-tab restoration,
and eligible-link behavior use the existing `plus` preferences. The Settings page
includes separate confirmation dialogs for PLUS history, cookies, WebView cache,
and WebView JavaScript storage. It links to existing downloads and Android's app
notification settings. WebView storage clearing cannot guarantee removal of every
kind of site data; private tabs are not storage-isolated. Deleting or canceling a
download now requests confirmation.

The English/Persian selector changes available translated text and layout direction,
but Persian coverage is incomplete (notably PRO+, some messages and Android
system UI). Other requested languages, independent date/number formats, remote search
suggestions, Do Not Track, choosing a download folder, clearing download history
without deleting files, automatic tab cleanup, and a site notification implementation
are not provided. Settings describes these omissions; it does not simulate them.
Tab restoration saves normal-tab addresses/titles, not full browser state. The
new-tab preference applies only to main-frame, user-initiated HTTP(S) GET navigations
that WebView exposes; popups, JavaScript and redirects are not guaranteed.

Build and JVM tests pass, but no Android device was available: Settings interactions,
persistence across real process restarts, RTL rendering, permissions, clearing, tabs,
downloads, actual search results, YouTube fullscreen and PRO+ require device testing.
The source archive contains a debug keystore for reproducible upgrades and must not be
published or used for production signing.

## PLUS Ad Blocker (debug build 10)

Open Settings → PLUS Ad Blocker. The master switch is **off by default**, to avoid
unexpected site breakage. Turning it on intercepts supported HTTP(S) WebView
subresource requests and responds with an empty response for selected bundled
advertising and tracking domains. Page navigations, unknown top-level sites and
same-host/parent-child requests are not blocked. An allowlisted site and its
subdomains bypass all rules; reload after changing a setting. Rules are exact-domain
and subdomain matches, not a general EasyList/ABP parser. The bundled lists are small,
static, and do not update automatically. Custom lists are not supported.

Advanced ad and tracker switches and detailed stats are gated by the existing
`ProUsage` default-browser status (`isDefault` AND `unlocked`); debug override alone
cannot enable them. Eligibility is cleared on pause and checked again on resume.
Advanced preferences remain stored when the role is lost but do not filter until
eligibility returns. Per-site controls use the same free allowlist. The global counter
counts blocked requests, not ads, and includes private-tab requests. The 60-domain
PRO+ breakdown excludes private tabs and stores blocked request domains, not pages.
These counts and settings are persisted in app-private preferences.

Filtering is limited to requests delivered through the existing WebView callback;
redirects, cache, service workers, app downloads, same-domain ads and embedded video
ads may bypass it. It does not guarantee anonymity or prevent all tracking. Some
sites can break; disable filtering or allowlist and reload them. No new permissions,
dependencies, background service, externally downloaded rules or external telemetry
were added.

JVM decision/statistics tests and the debug build pass. There was no available Android
device, so actual interception, persisted settings after restart, default-role
transitions, site compatibility, layout, and existing features including YouTube
fullscreen were not runtime tested.

## Ad Blocker toolbar and filtering update (debug build 11)

This section supersedes the build-10 list-size and same-site descriptions above.
The compact shield inside the address bar opens a panel whose on/off state and
all-time blocked-request count come from the real blocker. The panel can change the
master switch and open the existing Ad Blocker screen. A shield check means enabled;
a slash means disabled. Reload a page after changing the setting; already loaded or
cached content is not retroactively blocked. An allowed site remains exempt.

FREE blocking keeps the prior baseline rules and adds a conservative 43,831-domain
subset of EasyList version 202611050716, loaded once off the UI thread. Only plain,
unconditional hostname rules are included; rules implicated in recognizable
exceptions are omitted. Advanced ad/tracker hostnames remain PRO-gated even when
they appear in the imported set. The filter engine does not implement full EasyList
syntax, cosmetic rules, scriptlets, or automatic updates. A narrow same-site rule
for the WordPress `interstitial-ads` plugin is included; other first-party paths are
not generally blocked. Filtered requests receive an empty 403 with `no-store`.
The original EasyList snapshot, derivation script, attribution, and CC BY-SA 3.0
license are in `tools/filter-sources` and the notice/license are packaged as assets.
The EasyList authors: https://easylist.to/.

No Android device was available. The three Can You Block It pages were inspected
as static HTML, not run in this app. JVM policy tests pass, but toolbar interaction,
RTL, actual interception, default-browser transitions, site compatibility and
regression scenarios need device testing. Self-hosted/inline ads, popups, cosmetic
anti-blocker rules, WebView redirect destinations not exposed to the interception
callback, service workers, cache and video ads remain limitations.
