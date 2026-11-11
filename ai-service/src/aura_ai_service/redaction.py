import re
from dataclasses import dataclass
from typing import Any

_PRIVATE_KEY = (
    r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"
    r"[\s\S]*?"
    r"-----END (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"
)
_CREDENTIAL_KEY = (
    r"\b(?:api[ _-]?key|access[ _-]?token|refresh[ _-]?token|auth[ _-]?token|"
    r"client[ _-]?secret|private[ _-]?key|session[ _-]?token|password|passwd|"
    r"secret|credential|authorization)"
)
_CREDENTIAL_VALUE = (
    r"(?:\"[^\"\r\n]{4,}\"|'[^'\r\n]{4,}'|"
    r"(?:(?:Bearer|Basic)\s+)?[^\s,;&\"']{4,})"
)
_AUTH_TOKEN = (
    r"\b(?:Bearer|Basic)\s+(?!(?:authorization|authentication|scheme|credentials?)\b)"
    r"[A-Za-z0-9._~+/=-]{8,}"
)
_PROVIDER_TOKEN = (
    r"(?:AKIA|ASIA)[0-9A-Z]{16}"
    r"|gh[pousr]_[A-Za-z0-9_]{20,}"
    r"|github_pat_[A-Za-z0-9_]{20,}"
    r"|glpat-[A-Za-z0-9_-]{20,}"
    r"|xox[baprs]-[A-Za-z0-9-]{10,}"
    r"|AIza[0-9A-Za-z_-]{20,}"
    r"|GOCSPX-[A-Za-z0-9_-]{20,}"
    r"|hf_[A-Za-z0-9]{30,}"
    r"|sk-[A-Za-z0-9_-]{20,}"
    r"|(?:sk|rk)_(?:live|test)_[A-Za-z0-9]{12,}"
)
_SENSITIVE_PATTERN = re.compile(
    rf"(?P<private_key>{_PRIVATE_KEY})"
    rf"|(?P<credential_assignment>(?P<credential_key>{_CREDENTIAL_KEY})"
    rf"(?P<credential_separator>\s*[:=]\s*){_CREDENTIAL_VALUE})"
    rf"|(?P<auth_token>{_AUTH_TOKEN})"
    rf"|(?P<provider_token>{_PROVIDER_TOKEN})",
    re.IGNORECASE,
)


@dataclass
class RedactionSummary:
    private_key_blocks: int = 0
    auth_tokens: int = 0
    provider_tokens: int = 0
    credential_assignments: int = 0
    fields_redacted: int = 0

    @property
    def total_matches(self) -> int:
        return (
            self.private_key_blocks
            + self.auth_tokens
            + self.provider_tokens
            + self.credential_assignments
        )

    def note(self) -> str:
        counts = (
            ("private-key blocks", self.private_key_blocks),
            ("authorization tokens", self.auth_tokens),
            ("provider-formatted keys", self.provider_tokens),
            ("named credential values", self.credential_assignments),
        )
        matched = ", ".join(f"{name}: {count}" for name, count in counts if count)
        if not matched:
            matched = "no credential-like values matched"
        return (
            f"Credential pattern check before local processing: {self.total_matches} "
            f"value(s) across {self.fields_redacted} text field(s) were masked ({matched}). "
            "This pattern-based check cannot guarantee detection of every secret."
        )


def redact_payload(payload: dict[str, Any]) -> tuple[dict[str, Any], RedactionSummary]:
    """Copy a validated JSON payload while masking common credential patterns in text values."""
    summary = RedactionSummary()

    def redact_value(value: Any) -> Any:
        if isinstance(value, str):
            redacted = _SENSITIVE_PATTERN.sub(_replace_sensitive_value(summary), value)
            if redacted != value:
                summary.fields_redacted += 1
            return redacted
        if isinstance(value, list):
            return [redact_value(item) for item in value]
        if isinstance(value, dict):
            return {key: redact_value(item) for key, item in value.items()}
        return value

    return redact_value(payload), summary


def _replace_sensitive_value(summary: RedactionSummary):
    def replace(match: re.Match[str]) -> str:
        if match.group("private_key") is not None:
            summary.private_key_blocks += 1
            return "[REDACTED PRIVATE KEY]"
        if match.group("credential_assignment") is not None:
            summary.credential_assignments += 1
            prefix = match.group("credential_key") + match.group("credential_separator")
            return f"{prefix}[REDACTED]"
        if match.group("auth_token") is not None:
            summary.auth_tokens += 1
            scheme = match.group(0).split(maxsplit=1)[0]
            return f"{scheme} [REDACTED]"
        summary.provider_tokens += 1
        return "[REDACTED PROVIDER KEY]"

    return replace
