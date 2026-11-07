from functools import lru_cache
from typing import Literal

from pydantic import Field
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


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()


