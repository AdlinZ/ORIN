from app.core.config import Settings


def test_backend_url_reads_orin_backend_url(monkeypatch):
    monkeypatch.setenv("ORIN_BACKEND_URL", "http://orin-backend:8080")

    settings = Settings(_env_file=None)

    assert settings.BACKEND_URL == "http://orin-backend:8080"
    assert settings.ORIN_BACKEND_URL == "http://orin-backend:8080"


def test_legacy_backend_url_setter_updates_backend_url():
    settings = Settings(_env_file=None)

    settings.ORIN_BACKEND_URL = "http://backend.test"

    assert settings.BACKEND_URL == "http://backend.test"
    assert settings.ORIN_BACKEND_URL == "http://backend.test"
