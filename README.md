# Location Tracker

Two separate parts:

| Folder     | What                                                                 |
|------------|----------------------------------------------------------------------|
| `android/` | Kotlin Android app. Sends GPS location + device ID + timestamp every 3 s |
| `backend/` | Python (FastAPI) REST API, packaged as a Docker container, SQLite storage |

## API

All `/api/*` endpoints require header `X-API-Key: <key>` when the server has `API_KEY` set.

| Method | Path                                   | Purpose                        |
|--------|----------------------------------------|--------------------------------|
| GET    | `/`                                    | Health check                   |
| POST   | `/api/location`                        | Phone sends a location         |
| GET    | `/api/location/latest?device_id=XYZ`   | Latest location of one device  |
| GET    | `/api/locations?device_id=XYZ&limit=100` | History (newest first)       |
| GET    | `/api/devices`                         | All devices + last seen        |
| GET    | `/docs`                                | Interactive Swagger UI         |

POST body:

```json
{
  "device_id": "a1b2c3d4e5f60708",
  "latitude": 12.971599,
  "longitude": 77.594566,
  "accuracy": 4.8,
  "timestamp": "2026-10-25T10:15:30.123Z"
}
```

## 1. Run the backend locally

With Docker:

```bash
cd backend
docker compose up --build
```

Without Docker:

```bash
cd backend
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Open http://localhost:8000/docs. Tests: `pip install httpx pytest && pytest`.

## 2. Deploy the backend for free (Render.com)

1. Push this folder to a GitHub repository.
2. Sign in at https://render.com with GitHub → **New** → **Blueprint** → pick the repo.
   Render reads `render.yaml`, builds `backend/Dockerfile` and creates a **free** web service.
3. When it's live, open the service → **Environment** → copy the generated `API_KEY`.
4. Your URL looks like `https://location-tracker-api.onrender.com`. Check `/docs`.

Free-tier caveats:
- The service sleeps after 15 min with no traffic. The first request then takes ~30–60 s to wake it up. While the phone is tracking, it stays awake.
- The disk is **ephemeral**: the SQLite data is wiped on every redeploy/restart. For permanent storage, use a free Postgres database (e.g. Neon or Supabase). That needs a small code change.

Alternative free Docker hosts: Koyeb, Google Cloud Run (free tier, needs a billing account), Hugging Face Spaces (Docker).

## 3. Build & install the Android app

1. Install **Android Studio** (it bundles the JDK and Android SDK).
2. **File → Open** → select the `android/` folder. Let Gradle sync. If prompted, accept the Gradle wrapper and plugin upgrades.
3. Plug in your phone (USB debugging on) and press **Run**, or **Build → Build APK(s)** to get
   `android/app/build/outputs/apk/debug/app-debug.apk`.
4. In the app, enter:
   - **Server URL**: `https://location-tracker-api.onrender.com` (or `http://<your-PC-LAN-IP>:8000` for local testing)
   - **API key**: the value from Render
5. Tap **Start tracking** and allow location and notification permissions. A notification shows while tracking runs. The status area shows each send result.

To keep tracking when the screen is off, set the app to *Battery → Unrestricted* in Android settings.
