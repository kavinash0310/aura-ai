import re
from functools import lru_cache
from ipaddress import ip_address
from typing import Literal
from urllib.parse import urlsplit

from pydantic import Field, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="AURA_AI_",
        case_sensitive=False,
        extra="ignore",
    )

    service_name: str = Field(default="aura-ai-service", min_length=1, max_length=100)
    service_version: str = Field(default="0.1.0", min_length=1, max_length=30)
    log_level: Literal["DEBUG", "INFO", "WARNING", "ERROR"] = "INFO"
    internal_token: str = Field(default="", max_length=256)
    model_provider: Literal["disabled", "ollama"] = "disabled"
    ollama_base_url: str = Field(default="", max_length=300)
    ollama_model: str = Field(default="", max_length=100)
    ollama_timeout_seconds: int = Field(default=90, ge=5, le=180)

    @field_validator("internal_token")
    @classmethod
    def validate_internal_token(cls, value: str) -> str:
        if value and len(value) < 24:
            raise ValueError("Internal service token must contain at least 24 characters")
        if value and not re.fullmatch(r"[A-Za-z0-9_-]+", value):
            raise ValueError("Internal service token must use URL-safe characters")
        return value

    @field_validator("ollama_base_url")
    @classmethod
    def validate_ollama_base_url(cls, value: str) -> str:
        if not value:
            return value
        parsed = urlsplit(value)
        if parsed.scheme != "http" or not parsed.hostname or parsed.username or parsed.password:
            raise ValueError("Ollama must use a local HTTP URL without credentials")
        if parsed.port != 11434 or parsed.path not in ("", "/") or parsed.query or parsed.fragment:
            raise ValueError("Ollama URL must use port 11434 and contain only an origin")

        hostname = parsed.hostname.casefold()
        allowed_names = {"localhost", "host.docker.internal", "ollama"}
        if hostname not in allowed_names:
            try:
                address = ip_address(hostname)
            except ValueError as error:
                raise ValueError("Ollama host must be local or a private IP address") from error
            if (
                not address.is_private
                or address.is_loopback
                or address.is_link_local
                or address.is_unspecified
                or address.is_multicast
                or address.is_reserved
            ):
                raise ValueError("Ollama IP must be on a private local network")
        return value.rstrip("/")

    @model_validator(mode="after")
    def require_ollama_configuration(self) -> "Settings":
        if self.model_provider == "ollama":
            if not self.internal_token:
                raise ValueError("AURA_AI_INTERNAL_TOKEN is required for report generation")
            if not self.ollama_base_url or not self.ollama_model:
                raise ValueError("Ollama URL and model are required when Ollama is enabled")
        return self


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()


