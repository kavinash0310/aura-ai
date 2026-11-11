"""Local structured-output model providers used by the AURA AI service.

The interface keeps prompt construction and output validation in the routes while
isolating provider-specific request formats and response telemetry here.
"""

import json
from collections.abc import Callable, Sequence
from dataclasses import dataclass
from typing import Protocol
from urllib.request import HTTPRedirectHandler, Request, build_opener


MAX_OLLAMA_RESPONSE_BYTES = 64 * 1024


@dataclass(frozen=True)
class OllamaCallResult:
    """Raw content and optional provider metrics returned by Ollama transport."""

    content: str
    total_duration_ns: int | None = None
    prompt_eval_count: int | None = None
    eval_count: int | None = None


@dataclass(frozen=True)
class ModelCompletion:
    """Provider-neutral completion result consumed by API routes."""

    content: str
    provider: str
    model: str
    duration_ms: int | None = None
    prompt_token_count: int | None = None
    output_token_count: int | None = None


class StructuredModelProvider(Protocol):
    """Small interface for a local model that returns schema-constrained output."""

    def generate_structured(
        self,
        messages: Sequence[dict[str, str]],
        *,
        response_schema: dict[str, object],
        temperature: float,
        max_tokens: int,
    ) -> ModelCompletion: ...


class _NoRedirectHandler(HTTPRedirectHandler):
    def redirect_request(
        self,
        request: Request,
        response: object,
        code: int,
        message: str,
        headers: object,
        new_url: str,
    ) -> None:
        return None


def _optional_nonnegative_int(value: object) -> int | None:
    if isinstance(value, int) and not isinstance(value, bool) and 0 <= value <= 2**63 - 1:
        return value
    return None


def call_ollama(base_url: str, body: bytes, timeout_seconds: int) -> OllamaCallResult:
    """Call one configured Ollama endpoint without redirects or unbounded reads."""
    request = Request(
        f"{base_url}/api/chat",
        data=body,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    opener = build_opener(_NoRedirectHandler)
    with opener.open(request, timeout=timeout_seconds) as response:
        raw = response.read(MAX_OLLAMA_RESPONSE_BYTES + 1)
    if len(raw) > MAX_OLLAMA_RESPONSE_BYTES:
        raise ValueError("Local model response exceeded the size limit")
    outer = json.loads(raw)
    if not isinstance(outer, dict):
        raise ValueError("Local model returned an invalid response envelope")
    message = outer.get("message")
    if not isinstance(message, dict):
        raise ValueError("Local model response is missing its message")
    content = message.get("content")
    if not isinstance(content, str) or not content:
        raise ValueError("Local model returned no report content")
    return OllamaCallResult(
        content=content,
        total_duration_ns=_optional_nonnegative_int(outer.get("total_duration")),
        prompt_eval_count=_optional_nonnegative_int(outer.get("prompt_eval_count")),
        eval_count=_optional_nonnegative_int(outer.get("eval_count")),
    )


OllamaTransport = Callable[[str, bytes, int], OllamaCallResult | str]


class OllamaStructuredProvider:
    """Adapt AURA's generic structured completion request to Ollama's chat API."""

    provider = "ollama"

    def __init__(
        self,
        *,
        base_url: str,
        model: str,
        timeout_seconds: int,
        transport: OllamaTransport = call_ollama,
    ) -> None:
        self.base_url = base_url
        self.model = model
        self.timeout_seconds = timeout_seconds
        self._transport = transport

    def generate_structured(
        self,
        messages: Sequence[dict[str, str]],
        *,
        response_schema: dict[str, object],
        temperature: float,
        max_tokens: int,
    ) -> ModelCompletion:
        if not 0 <= temperature <= 2:
            raise ValueError("Model temperature must be between 0 and 2")
        if not 1 <= max_tokens <= 8192:
            raise ValueError("Model output token limit must be between 1 and 8192")
        payload = json.dumps(
            {
                "model": self.model,
                "messages": list(messages),
                "format": response_schema,
                "stream": False,
                "options": {"temperature": temperature, "num_predict": max_tokens},
            },
            ensure_ascii=False,
        ).encode("utf-8")
        result = self._transport(self.base_url, payload, self.timeout_seconds)
        if isinstance(result, str):
            return ModelCompletion(
                content=result,
                provider=self.provider,
                model=self.model,
            )
        return ModelCompletion(
            content=result.content,
            provider=self.provider,
            model=self.model,
            duration_ms=(
                result.total_duration_ns // 1_000_000
                if result.total_duration_ns is not None
                else None
            ),
            prompt_token_count=result.prompt_eval_count,
            output_token_count=result.eval_count,
        )


__all__ = [
    "OllamaCallResult",
    "OllamaStructuredProvider",
    "StructuredModelProvider",
    "call_ollama",
]
