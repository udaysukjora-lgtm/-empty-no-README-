# Messaging App — Android Client

Kotlin + Jetpack Compose client for the FastAPI backend in `../main.py` (phone-OTP auth, conversations, real-time chat over WebSocket).

## Screens
- **Login** — enter phone number → `POST /auth/send-otp` → enter OTP → `POST /auth/verify-otp`, saves the JWT with DataStore.
- **Conversations** — `GET /conversations`; the `+` FAB starts a new one via `POST /conversations` with the other user's phone number.
- **Chat** — loads history from `GET /conversations/{id}/messages`, then connects to `ws://.../ws?token=...` for live `message:new` / `typing:*` events and sends messages over the socket.

## Run it
1. Start the backend (from the repo root):
   ```bash
   pip install -r requirements.txt
   uvicorn main:app --reload
   ```
2. Open the `android/` folder in Android Studio (Koala+) and let it sync — the Gradle wrapper jar is generated automatically on first sync/build (`File → Sync Project with Gradle Files`), or run `gradle wrapper` once if you have Gradle installed locally.
3. Run on an emulator — `NetworkModule.BASE_URL`/`WS_URL` (`app/src/main/java/com/example/messagingapp/data/remote/NetworkModule.kt`) already point at `10.0.2.2:8000`, the emulator's alias for your host machine's `localhost`.
4. For a **physical device**, change `BASE_URL`/`WS_URL` to your machine's LAN IP or a deployed URL (both devices must be able to reach the backend).

## Notes
- The dev backend's OTP is hardcoded to `123456` (see `DEV_OTP` in `main.py`) — the login screen shows a hint for this.
- Two devices/emulators with different phone numbers can message each other by starting a conversation with the peer's phone number.
- `android:usesCleartextTraffic="true"` is set for local `http://`/`ws://` testing — switch to `https://`/`wss://` and drop that flag for a real deployment.
