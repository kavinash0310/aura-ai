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
        "promptVersion": "research-query-plan-v2",
        "durationMs": 1200,
        "promptTokenCount": 321,
        "outputTokenCount": 42,
    }
    assert "search providers" in response.json()["note"]
    messages = captured["payload"]["messages"]
    assert "user will review and edit every suggestion" in messages[0]["content"]


def test_query_plan_redacts_credentials_before_local_model_inference(monkeypatch) -> None:
    from aura_ai_service import main

    captured: dict[str, object] = {}

    def fake_call(_base_url, payload, _timeout):
        captured["payload"] = json.loads(payload)
        return main.OllamaCallResult(content=json.dumps({"suggestions": ["official timeline"]}))

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
            json={"topic": "passkey adoption api_key=topic-secret-value"},
        )

    assert response.status_code == 200
    prompt = json.loads(captured["payload"]["messages"][1]["content"])
    assert "topic-secret-value" not in json.dumps(prompt)
    assert prompt["topic"] == "passkey adoption api_key=[REDACTED]"
    assert "1 value(s) across 1 text field(s) were masked" in response.json()["note"]


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
    assert response.json()["coverageNote"].startswith("AI-generated")
    assert response.json()["coverageNote"].endswith(
        "This pattern-based check cannot guarantee detection of every secret."
    )
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


def test_report_redacts_credentials_before_local_model_inference(monkeypatch) -> None:
    from aura_ai_service import main

    captured: dict[str, object] = {}

    def fake_call(_base_url, payload, _timeout):
        captured["payload"] = json.loads(payload)
        return main.OllamaCallResult(content=report_content())

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
            "/api/v1/research/report",
            headers={"Authorization": f"Bearer {TOKEN}"},
            json={
                "query": "public API key=topic-secret-value-123",
                "sources": [
                    {
                        "id": SOURCE_ID,
                        "title": "Passkey overview",
                        "url": "https://a.example/?access_token=url-secret-value-123",
                        "domain": "a.example",
                        "kind": "WEB",
                        "description": (
                            "Bearer source-secret-token-123 and ghp_12345678901234567890"
                        ),
                    }
                ],
            },
        )

    assert response.status_code == 200
    prompt = json.loads(captured["payload"]["messages"][1]["content"])
    prompt_text = json.dumps(prompt)
    assert "topic-secret-value-123" not in prompt_text
    assert "url-secret-value-123" not in prompt_text
    assert "source-secret-token-123" not in prompt_text
    assert "ghp_12345678901234567890" not in prompt_text
    assert response.json()["coverageNote"].startswith("AI-generated")
    assert "4 value(s) across 3 text field(s) were masked" in response.json()["coverageNote"]
    assert "named credential values: 2" in response.json()["coverageNote"]
    assert "authorization tokens: 1" in response.json()["coverageNote"]
    assert "provider-formatted keys: 1" in response.json()["coverageNote"]


def test_claim_comparison_redacts_sensitive_input_and_returns_safe_claim(monkeypatch) -> None:
    from aura_ai_service import main

    captured: dict[str, object] = {}
    comparison = json.dumps(
        {
            "assessment": "INSUFFICIENT",
            "explanation": "The selected excerpts do not directly address the claim.",
            "evidence": [],
            "limitations": ["The provider snippets were not independently verified."],
        }
    )

    def fake_call(_base_url, payload, _timeout):
        captured["payload"] = json.loads(payload)
        return main.OllamaCallResult(content=comparison)

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
            "/api/v1/research/compare",
            headers={"Authorization": f"Bearer {TOKEN}"},
            json={
                "claim": "api_key=claim-secret-value-123 is a passkey",
                "sources": [
                    {
                        "id": SOURCE_ID,
                        "title": "Passkey overview",
                        "url": "https://a.example/passkeys",
                        "domain": "a.example",
                        "kind": "WEB",
                    }
                ],
            },
        )

    assert response.status_code == 200
    prompt = json.loads(captured["payload"]["messages"][1]["content"])
    assert "claim-secret-value-123" not in json.dumps(prompt)
    assert response.json()["claim"] == "api_key=[REDACTED] is a passkey"
    assert "1 value(s) across 1 text field(s) were masked" in response.json()["coverageNote"]


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
