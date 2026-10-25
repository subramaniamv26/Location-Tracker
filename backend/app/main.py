"""Location Tracker API: receives device locations and lets you query them."""
import os
import sqlite3
from contextlib import asynccontextmanager, contextmanager
from datetime import datetime, timezone
from typing import Optional

from fastapi import Depends, FastAPI, Header, HTTPException, Query
from pydantic import BaseModel, Field

DB_PATH = os.getenv("DB_PATH", "locations.db")
API_KEY = os.getenv("API_KEY", "")  # empty = no auth (local testing only)

@contextmanager
def db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def init_db() -> None:
    os.makedirs(os.path.dirname(os.path.abspath(DB_PATH)), exist_ok=True)
    with db() as conn:
        conn.execute(
            """CREATE TABLE IF NOT EXISTS locations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                accuracy REAL,
                timestamp TEXT NOT NULL,
                received_at TEXT NOT NULL
            )"""
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_device_time ON locations(device_id, id)"
        )


@asynccontextmanager
async def lifespan(_app: FastAPI):
    init_db()
    yield


app = FastAPI(title="Location Tracker API", version="1.0.0", lifespan=lifespan)


def check_api_key(x_api_key: Optional[str] = Header(default=None)) -> None:
    if API_KEY and x_api_key != API_KEY:
        raise HTTPException(status_code=401, detail="Invalid or missing X-API-Key")


class LocationIn(BaseModel):
    device_id: str = Field(min_length=1, max_length=128)
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    accuracy: Optional[float] = Field(default=None, ge=0)
    timestamp: datetime  # ISO-8601, set by the phone


class LocationOut(LocationIn):
    id: int
    received_at: datetime


@app.get("/")
def health():
    return {"status": "ok", "service": "location-tracker"}


@app.post("/api/location", response_model=LocationOut, status_code=201,
          dependencies=[Depends(check_api_key)])
def post_location(loc: LocationIn):
    received_at = datetime.now(timezone.utc).isoformat()
    with db() as conn:
        cur = conn.execute(
            "INSERT INTO locations (device_id, latitude, longitude, accuracy, timestamp, received_at)"
            " VALUES (?, ?, ?, ?, ?, ?)",
            (loc.device_id, loc.latitude, loc.longitude, loc.accuracy,
             loc.timestamp.isoformat(), received_at),
        )
        return {**loc.model_dump(), "id": cur.lastrowid, "received_at": received_at}


@app.get("/api/location/latest", response_model=LocationOut,
         dependencies=[Depends(check_api_key)])
def latest_location(device_id: str):
    with db() as conn:
        row = conn.execute(
            "SELECT * FROM locations WHERE device_id = ? ORDER BY id DESC LIMIT 1",
            (device_id,),
        ).fetchone()
    if row is None:
        raise HTTPException(status_code=404, detail="No location for this device")
    return dict(row)


@app.get("/api/locations", response_model=list[LocationOut],
         dependencies=[Depends(check_api_key)])
def list_locations(device_id: Optional[str] = None,
                   limit: int = Query(default=100, ge=1, le=1000)):
    sql, args = "SELECT * FROM locations", []
    if device_id:
        sql += " WHERE device_id = ?"
        args.append(device_id)
    sql += " ORDER BY id DESC LIMIT ?"
    args.append(limit)
    with db() as conn:
        return [dict(r) for r in conn.execute(sql, args).fetchall()]


@app.get("/api/devices", dependencies=[Depends(check_api_key)])
def list_devices():
    with db() as conn:
        rows = conn.execute(
            "SELECT device_id, COUNT(*) AS points, MAX(timestamp) AS last_seen"
            " FROM locations GROUP BY device_id ORDER BY last_seen DESC"
        ).fetchall()
    return [dict(r) for r in rows]
