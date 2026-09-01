# AskQustion (Android)

Native Android client (Kotlin + Jetpack Compose) for **https://www.askqustion.in**,
built against the standard WordPress REST API (`/wp-json/wp/v2/...`).

## How "questions" and "answers" map to WordPress

This app doesn't assume any particular Q&A plugin — it wasn't possible to
inspect the live site's REST API from this sandbox (outbound network access
here is restricted to an allowlist that didn't include this domain), so it's
built on WordPress's own core REST endpoints, which every WordPress site
exposes regardless of plugins:

- **Question** = a WordPress **post** (`wp/v2/posts`)
- **Answer** = a **comment** on that post (`wp/v2/comments`)
- **Asking** = creating a post (`POST wp/v2/posts`)
- **Answering** = creating a comment (`POST wp/v2/comments`)

If askqustion.in actually runs a dedicated Q&A plugin (AnsPress, DW Question
& Answer, etc.) with its own question/answer post types and REST endpoints,
this app will still work for anything published as a regular post, but it
won't see content stored under that plugin's custom post types. Tell me the
plugin name and I can point `WpApiService` at its actual endpoints instead —
see `app/src/main/java/com/example/askqustion/data/remote/WpApiService.kt`.

## Features

- Browse questions (`GET wp/v2/posts`, with search)
- Open a question: full content + its comments as answers
- Post an answer (requires login)
- Ask a new question (requires login) — tries to publish directly, falls
  back to "pending review" if the account's role can't publish directly
- Login via WordPress **Application Passwords** (see below) — no password
  reset flow needed, and it's revocable independently of your real login
  password

## Login: Application Passwords, not your WordPress password

This app authenticates writes (asking/answering) with HTTP Basic Auth using
a WordPress **Application Password**, not your normal wp-admin password:

1. In wp-admin: **Users → Profile** (or **Users → All Users → [your user]**)
2. Scroll to **Application Passwords**, give it a name like "AskQustion
   Android app", click **Add New Application Password**
3. WordPress shows the password once — copy it (it looks like `xxxx xxxx
   xxxx xxxx xxxx xxxx`) and paste it into the app's login screen along
   with your username

Application Passwords are a WordPress core feature (5.6+) and require the
site to be served over **HTTPS** (askqustion.in already is). You can revoke
one from the same profile screen at any time without changing your main
password.

## Project layout

```
askqustion-app/
  app/src/main/java/com/example/askqustion/
    config/Config.kt                REST API base URL
    data/model/WpModels.kt          WpPost, WpComment, WpCategory, etc.
    data/local/CredentialStore.kt   DataStore-backed username/app-password
    data/remote/                    Retrofit WpApiService, Basic-Auth OkHttp setup
    data/repository/                AuthRepository, QaRepository
    ui/questions/                   question list (home) + question detail/answers
    ui/ask/                         "ask a question" form
    ui/login/                       WordPress login screen
    ui/nav/AppNavHost.kt            questions -> question detail / ask / login
```

## Point it at a different URL

Edit `BASE_URL` in `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "BASE_URL", "\"https://www.askqustion.in/wp-json/\"")
```

## Build & run

Requires **Android Studio** (or the Android SDK/build-tools locally) — this
sandboxed environment has no network access to `dl.google.com`/
`maven.google.com`, so the Android Gradle Plugin and SDK components could
not be downloaded or compiled here, and this project has not been built or
run yet. The Gradle wrapper (`gradlew`) is included and pinned to Gradle
8.14.3; open the `askqustion-app/` folder in Android Studio and let it sync,
or from a machine with normal internet access:

```bash
cd askqustion-app
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`.
