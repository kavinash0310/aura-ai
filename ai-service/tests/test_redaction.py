from aura_ai_service.redaction import redact_payload


def test_redacts_auth_credentials_provider_keys_and_private_key_blocks() -> None:
    private_key = (
        "-----BEGIN PRIVATE KEY-----\nexample-private-material\n"
        "-----END PRIVATE KEY-----"
    )
    payload = {
        "query": "Bearer abcdefghijklmnop",
        "sources": [
            {
                "title": "api_key=service-secret-value",
                "description": f"ghp_12345678901234567890 {private_key}",
            }
        ],
    }

    redacted, summary = redact_payload(payload)

    assert redacted["query"] == "Bearer [REDACTED]"
    assert redacted["sources"][0]["title"] == "api_key=[REDACTED]"
    assert "ghp_12345678901234567890" not in redacted["sources"][0]["description"]
    assert "example-private-material" not in redacted["sources"][0]["description"]
    assert summary.auth_tokens == 1
    assert summary.credential_assignments == 1
    assert summary.provider_tokens == 1
    assert summary.private_key_blocks == 1
    assert summary.fields_redacted == 3
    assert summary.total_matches == 4


def test_does_not_redact_ordinary_public_text_or_mutate_the_input() -> None:
    payload = {"query": "The public key rotation announcement describes new research."}

    redacted, summary = redact_payload(payload)

    assert redacted == payload
    assert redacted is not payload
    assert summary.total_matches == 0
    assert summary.fields_redacted == 0
    assert "no credential-like values matched" in summary.note()


def test_redacts_credentials_in_query_parameters() -> None:
    payload = {"url": "https://example.test/article?access_token=long-secret-value&view=full"}

    redacted, summary = redact_payload(payload)

    assert redacted["url"] == "https://example.test/article?access_token=[REDACTED]&view=full"
    assert summary.credential_assignments == 1
