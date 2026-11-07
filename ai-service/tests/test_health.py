import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from aura_ai_service.config import Settings
from aura_ai_service.main import create_app


def test_health_response_uses_service_defaults() -> None:
    with TestClient(create_app(Settings())) as client:
        response = client.get("/api/v1/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "service": "aura-ai-service",
        "version": "0.1.0",
    }


def test_health_response_uses_configured_identity() -> None:
    settings = Settings(service_name="aura-ai-test", service_version="0.2.0")

    with TestClient(create_app(settings)) as client:
        response = client.get("/api/v1/health")

    assert response.status_code == 200
    assert response.json()["service"] == "aura-ai-test"
    assert response.json()["version"] == "0.2.0"


def test_invalid_log_level_is_rejected() -> None:
    with pytest.raises(ValidationError):
        Settings(log_level="TRACE")


