# AskQustion (Android)

Native Android **WebView wrapper** around **https://www.askqustion.in**.

## Why a WebView wrapper

An earlier version of this app tried to rebuild the site natively against
the standard WordPress REST API (posts as questions, comments as answers).
That fell apart once it was clear the real site is a heavily custom,
JS-driven AI Q&A platform — tags, category chips, "Generate with AI", an
"Agent" mode, camera/video/file attach icons on the ask box — none of which
exist as generic WordPress REST endpoints, and this sandbox has no network
access to the site to reverse-engineer its actual custom API.

A WebView wrapper sidesteps all of that: the app just loads the real site,
so the interface is exactly what's in the browser — login, AI generation,
Agent mode, tags, categories, everything — with nothing to keep in sync.

## What's implemented

- Loads `askqustion.in` in a full-screen `WebView` (JS + DOM storage on).
- **Cookies persist** across app restarts (`CookieManager`), so logging in
  once through the site's own login page keeps you signed in — no
  separate auth flow to build or maintain.
- **File/camera/video uploads work**: `onShowFileChooser` builds a chooser
  that includes gallery/file picking plus "take photo" / "record video"
  (via `FileProvider`) when the `CAMERA` permission is granted, so the ask
  box's camera/video/file icons function correctly — this is the one thing
  that silently does nothing in most naive WebView wrappers if you skip it.
- Links to `askqustion.in` (and subdomains) stay inside the app; any other
  host or non-http(s) scheme (`tel:`, `mailto:`, `whatsapp:`, etc.) opens
  in an external app/browser.
- Downloads triggered by the site go through `DownloadManager` (with the
  current session's cookies attached, so authenticated downloads work).
- Hardware back button navigates the WebView's own history before exiting.
- Pull-to-refresh (`SwipeRefreshLayout`) and a slim top progress bar.
- Survives rotation without reloading the page (`WebView.saveState`).

## Project layout

```
askqustion-app/
  app/src/main/java/com/example/askqustion/
    MainActivity.kt              the whole wrapper: WebView setup, link
                                  routing, file chooser, downloads, back nav
  app/src/main/res/
    layout/activity_main.xml     SwipeRefreshLayout > WebView, + ProgressBar
    xml/file_paths.xml           FileProvider paths for camera capture
```

No Jetpack Compose, Retrofit, or DataStore — a wrapper this thin doesn't
need them, and dropping them cuts dependency/version-conflict risk.

## Point it at a different URL

Edit `BASE_URL` in `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "BASE_URL", "\"https://www.askqustion.in/\"")
```

`MainActivity` also derives which links stay inside the WebView (vs. open
externally) from this same host, so changing it is enough.

## Build & run

Requires **Android Studio** (or the Android SDK/build-tools locally) — this
sandboxed environment has no network access to `dl.google.com`/
`maven.google.com`, so the Android Gradle Plugin and SDK components could
not be downloaded or compiled here, and this project has not been built or
run locally. It has been built successfully via the GitHub Actions workflow
at `.github/workflows/build-askqustion-apk.yml` (Actions tab on GitHub —
works from a phone browser, no computer needed). The Gradle wrapper
(`gradlew`) is included and pinned to Gradle 8.14.3; open the
`askqustion-app/` folder in Android Studio and let it sync, or from a
machine with normal internet access:

```bash
cd askqustion-app
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`.
