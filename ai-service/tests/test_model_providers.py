import json

import pytest

from aura_ai_service.model_providers import (
    MAX_OLLAMA_RESPONSE_BYTES,
    ModelCompletion,
    OllamaCallResult,
    OllamaStructuredProvider,
    call_ollama,
)


def test_ollama_adapter_maps_structured_completion_and_metrics() -> None:
    captured: dict[str, object] = {}

    def fake_transport(base_url: str, payload: bytes, timeout: int) -> OllamaCallResult:
        captured["base_url"] = base_url
        captured["payload"] = json.loads(payload)
        captured["timeout"] = timeout
        return OllamaCallResult(
            content='{"answer":"ok"}',
            total_duration_ns=1_234_000_000,
            prompt_eval_count=34,
            eval_count=12,
        )

    provider = OllamaStructuredProvider(
        base_url="http://localhost:11434",
        model="test-model",
        timeout_seconds=45,
        transport=fake_transport,
    )
    messages = [
        {"role": "system", "content": "Return JSON."},
        {"role": "user", "content": "café"},
    ]
    schema = {"type": "object", "properties": {"answer": {"type": "string"}}}

    result = provider.generate_structured(
        messages,
        response_schema=schema,
        temperature=0.2,
        max_tokens=350,
    )

    assert result == ModelCompletion(
        content='{"answer":"ok"}',
        provider="ollama",
        model="test-model",
        duration_ms=1234,
        prompt_token_count=34,
        output_token_count=12,
    )
    assert captured == {
        "base_url": "http://localhost:11434",
        "timeout": 45,
        "payload": {
            "model": "test-model",
            "messages": messages,
            "format": schema,
            "stream": False,
            "options": {"temperature": 0.2, "num_predict": 350},
        },
    }


def test_ollama_adapter_accepts_content_only_test_transport() -> None:
    provider = OllamaStructuredProvider(
        base_url="http://localhost:11434",
        model="test-model",
        timeout_seconds=45,
        transport=lambda *_: '{"answer":"ok"}',
    )

    result = provider.generate_structured(
        [],
        response_schema={"type": "object"},
        temperature=0.1,
        max_tokens=10,
    )

    assert result.content == '{"answer":"ok"}'
    assert result.provider == "ollama"
    assert result.duration_ms is None
    assert result.prompt_token_count is None
    assert result.output_token_count is None


def test_ollama_transport_caps_response_and_normalizes_invalid_metrics(monkeypatch) -> None:
    from aura_ai_service import model_providers

    captured: dict[str, object] = {}
    response_body = json.dumps(
        {
            "message": {"content": "{}"},
            "total_duration": True,
            "prompt_eval_count": -1,
            "eval_count": 7,
        }
    ).encode()

    class FakeResponse:
        def __enter__(self):
            return self

        def __exit__(self, *_args: object) -> None:
            return None

        def read(self, size: int) -> bytes:
            captured["read_size"] = size
            return response_body

    class FakeOpener:
        def open(self, request, timeout: int):
            captured["url"] = request.full_url
            captured["method"] = request.get_method()
            captured["timeout"] = timeout
            return FakeResponse()

    def fake_build_opener(*handlers: object) -> FakeOpener:
        captured["handler_count"] = len(handlers)
        return FakeOpener()

    monkeypatch.setattr(model_providers, "build_opener", fake_build_opener)

    result = call_ollama("http://localhost:11434", b"{}", 30)

    assert result == OllamaCallResult(
        content="{}",
        total_duration_ns=None,
        prompt_eval_count=None,
        eval_count=7,
    )
    assert captured == {
        "url": "http://localhost:11434/api/chat",
        "method": "POST",
        "timeout": 30,
        "read_size": MAX_OLLAMA_RESPONSE_BYTES + 1,
        "handler_count": 1,
    }


def test_ollama_transport_rejects_oversized_response(monkeypatch) -> None:
    from aura_ai_service import model_providers

    class FakeResponse:
        def __enter__(self):
            return self

        def __exit__(self, *_args: object) -> None:
            return None

        def read(self, size: int) -> bytes:
            return b"x" * size

    class FakeOpener:
        def open(self, *_args: object, **_kwargs: object) -> FakeResponse:
            return FakeResponse()

    monkeypatch.setattr(model_providers, "build_opener", lambda *_: FakeOpener())

    with pytest.raises(ValueError, match="exceeded the size limit"):
        call_ollama("http://localhost:11434", b"{}", 30)


@pytest.mark.parametrize(
    ("temperature", "max_tokens"),
    [(2.1, 10), (-0.1, 10), (0.1, 0), (0.1, 8193)],
)
def test_ollama_adapter_rejects_unsafe_generation_bounds(
    temperature: float,
    max_tokens: int,
) -> None:
    def unexpected_transport(*_args: object) -> str:
        pytest.fail("transport should not run for invalid generation bounds")

    provider = OllamaStructuredProvider(
        base_url="http://localhost:11434",
        model="test-model",
        timeout_seconds=45,
        transport=unexpected_transport,
    )

    with pytest.raises(ValueError):
        provider.generate_structured(
            [],
            response_schema={"type": "object"},
            temperature=temperature,
            max_tokens=max_tokens,
        )
