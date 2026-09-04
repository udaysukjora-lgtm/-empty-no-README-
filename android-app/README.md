# ChatApp (Android)

Native Android client (Kotlin + Jetpack Compose) for the FastAPI messaging
backend in this repo (`main.py`). It talks to the backend's REST endpoints
for auth/history and to `/ws` for live messaging.

## Features

- Phone number + OTP login (`/auth/send-otp`, `/auth/verify-otp`), JWT saved
  locally with DataStore.
- Conversations list (`GET /conversations`), start a new one by phone number
  (`POST /conversations`).
- Chat screen: loads history (`GET /conversations/{id}/messages`), then a
  live WebSocket connection to `/ws?token=...` for `message:send`,
  `message:new`, `message:read`, `message:status`, `typing:start/stop`.

## Project layout

```
android-app/
  app/src/main/java/com/example/chatapp/
    config/Config.kt              backend base URL / derived ws URL
    data/model/                   request/response models
    data/local/SessionManager.kt  DataStore-backed JWT storage
    data/remote/                  Retrofit ApiService, OkHttp+auth setup, WebSocket client
    data/repository/              AuthRepository, ChatRepository
    ui/login/                     phone+OTP screen
    ui/conversations/             conversation list + "start new" dialog
    ui/chat/                      message list + composer, live via WebSocket
    ui/nav/AppNavHost.kt          login -> conversations -> chat routing
```

## Point it at your backend

Edit `BASE_URL` in `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8000/\"")
```

- `10.0.2.2` is the Android **emulator's** alias for your computer's
  `localhost` — use it while running `uvicorn main:app` locally and testing
  in the emulator.
- On a **physical device**, use your machine's LAN IP instead (e.g.
  `http://192.168.1.20:8000/`), or deploy the backend and use its real URL.
- For a production/public backend, use `https://...` and then delete
  `app/src/main/res/xml/network_security_config.xml` and its reference in
  `AndroidManifest.xml` — that file only exists to allow plaintext `http://`
  to the emulator/localhost during development.

## Build & run

Requires **Android Studio** (Koala/2024.1+) or the Android SDK/build-tools
installed locally — this sandboxed environment has no network access to
`dl.google.com`/`maven.google.com`, so the Android Gradle Plugin and SDK
components could not be downloaded or compiled here. The Gradle wrapper
(`gradlew`) is included and pinned to Gradle 8.14.3; open the `android-app/`
folder in Android Studio and it will sync and offer to install any missing
SDK platform/build-tools, or from a machine with normal internet access run:

```bash
cd android-app
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`.

## Backend

Start the backend this app talks to from the repo root:

```bash
pip install -r requirements.txt
uvicorn main:app --reload
```

Note: `main.py`'s OTP flow is a dev stub (hardcoded OTP `123456`, see
`DEV_OTP` in `main.py`) and its DB defaults to a local MySQL instance — see
the `TODO`s in `main.py` before using this against anything but a local dev
setup.
