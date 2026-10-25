import os, tempfile
os.environ["DB_PATH"] = os.path.join(tempfile.mkdtemp(), "test.db")
os.environ["API_KEY"] = "secret"

from fastapi.testclient import TestClient
from app.main import app

H = {"X-API-Key": "secret"}


def test_flow():
    with TestClient(app) as c:
        assert c.get("/").json()["status"] == "ok"
        body = {"device_id": "abc123", "latitude": 12.97, "longitude": 77.59,
                "accuracy": 5.0, "timestamp": "2026-10-25T10:00:00Z"}
        assert c.post("/api/location", json=body).status_code == 401
        r = c.post("/api/location", json=body, headers=H)
        assert r.status_code == 201, r.text
        body["latitude"] = 13.0
        c.post("/api/location", json=body, headers=H)
        latest = c.get("/api/location/latest", params={"device_id": "abc123"}, headers=H).json()
        assert latest["latitude"] == 13.0
        assert len(c.get("/api/locations", headers=H).json()) == 2
        assert c.get("/api/devices", headers=H).json()[0]["points"] == 2
        bad = dict(body, latitude=200)
        assert c.post("/api/location", json=bad, headers=H).status_code == 422
