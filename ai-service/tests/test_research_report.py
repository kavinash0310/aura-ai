import json

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from aura_ai_service.config import Settings
from aura_ai_service.main import create_app

TOKEN = "unit-test-internal-token-0123456789"
SOURCE_ID = "8d81d665-02a0-4a7d-98aa-4f41c2f6cabe"


def report_content(source_id: str = SOURCE_ID) -> str:
    return json.dumps(
        {
            "title": "Passkey research",
            "summary": "The selected sources describe growing passkey adoption.",
            "summarySourceIds": [source_id],
            "findings": [{"text": "Two sources discuss passkeys.", "sourceIds": [source_id]}],
            "limitations": ["The linked pages were not fetched or independently checked."],
        }
    )


def test_report_requires_internal_service_token() -> None:
    app = create_app(
        Settings(
            internal_token=TOKEN,
            model_provider="ollama",
            ollama_base_url="http://localhost:11434",
            ollama_model="test-model",
        )
    )
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/research/report",
            json={
                "query": "passkeys",
                "sources": [
                    {
                        "id": SOURCE_ID,
                        "title": "A",
                        "url": "https://a.example",
                        "domain": "a.example",
                        "kind": "WEB",
                    }
                ],
            },
        )

    assert response.status_code == 401


def test_query_plan_suggests_distinct_angles_and_returns_model_telemetry(monkeypatch) -> None:
    from aura_ai_service import main

    captured: dict[str, object] = {}

    def fake_call(_base_url, payload, _timeout):
        captured["payload"] = json.loads(payload)
        return main.OllamaCallResult(
            content=json.dumps(
                {
                    "suggestions": [
                        "  passkeys   adoption  ",
                        "public agency statement",
                    ]
                }
            ),
            total_duration_ns=1_200_000_000,
            prompt_eval_count=321,
            eval_count=42,
        )

    monkeypatch.setattr(main, "_call_ollama", fake_call)
    app = create_app(
        Settings(
            internal_token=TOKEN,
            model_provider="ollama",
            ollama_base_url="http://localhost:11434",
            ollama_model="test-model",
        )
    )
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/research/query-plan",
            headers={"Authorization": f"Bearer {TOKEN}"},
            json={"topic": "passkeys adoption"},
        )

    assert response.status_code == 200
    assert response.json()["suggestions"] == ["public agency statement"]
    assert response.json()["modelRun"] == {
        "promptVersion": "research-query-plan-v1",
        "durationMs": 1200,
        "promptTokenCount": 321,
        "outputTokenCount": 42,
    }
    assert "search providers" in response.json()["note"]
    messages = captured["payload"]["messages"]
    assert "user will review and edit every suggestion" in messages[0]["content"]


def test_query_plan_requires_internal_token_and_local_model() -> None:
    app = create_app(
        Settings(
            internal_token=TOKEN,
            model_provider="disabled",
            ollama_base_url="",
            ollama_model="",
        )
    )
    with TestClient(app) as client:
        unauthorized = client.post("/api/v1/research/query-plan", json={"topic": "topic"})
        disabled = client.post(
            "/api/v1/research/query-plan",
            headers={"Authorization": f"Bearer {TOKEN}"},
            json={"topic": "topic"},
        )

    assert unauthorized.status_code == 401
    assert disabled.status_code == 503


def test_report_returns_only_validated_source_citations(monkeypatch) -> None:
    from aura_ai_service import main

    monkeypatch.setattr(main, "_call_ollama", lambda *_: report_content())
    app = create_app(
        Settings(
            internal_token=TOKEN,
            model_provider="ollama",
            ollama_base_url="http://localhost:11434",
            ollama_model="test-model",
        )
    )
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/research/report",
            headers={"Authorization": f"Bearer {TOKEN}"},
            json={
                "query": "passkeys",
                "sources": [
                    {
                        "id": SOURCE_ID,
                        "title": "Passkey overview",
                        "url": "https://a.example/passkeys",
                        "domain": "a.example",
                        "kind": "WEB",
                        "description": "A short provider snippet.",
                    }
                ],
            },
        )

    assert response.status_code == 200
    assert response.json()["provider"] == "ollama"
    assert response.json()["summarySourceIds"] == [SOURCE_ID]
    assert response.json()["citations"] == [
        {
            "id": SOURCE_ID,
            "title": "Passkey overview",
            "url": "https://a.example/passkeys",
            "domain": "a.example",
            "kind": "WEB",
        }
    ]
    assert "not included" in response.json()["coverageNote"]


def test_report_rejects_model_citations_outside_selected_source_set(monkeypatch) -> None:
    from aura_ai_service import main

    monkeypatch.setattr(main, "_call_ollama", lambda *_: report_content("another-source"))
    app = create_app(
        Settings(
            internal_token=TOKEN,
            model_provider="ollama",
            ollama_base_url="http://localhost:11434",
            ollama_model="test-model",
        )
    )
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/research/report",
            headers={"Authorization": f"Bearer {TOKEN}"},
            json={
                "query": "passkeys",
                "sources": [
                    {
                        "id": SOURCE_ID,
                        "title": "A",
                        "url": "https://a.example",
                        "domain": "a.example",
                        "kind": "WEB",
                    }
                ],
            },
        )

    assert response.status_code == 502
    assert "invalid source citation" in response.json()["detail"]


def test_model_settings_require_local_endpoint_and_internal_token() -> None:
    with pytest.raises(ValidationError):
        Settings(
            model_provider="ollama",
            internal_token=TOKEN,
            ollama_base_url="https://example.com:11434",
            ollama_model="test-model",
        )

    with pytest.raises(ValidationError):
        Settings(
            model_provider="ollama",
            internal_token=TOKEN,
            ollama_base_url="http://localhost:11434",
        )
