"""Measure the current redaction patterns against a small synthetic fixture set."""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any

SERVICE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE_ROOT / "src"))

from aura_ai_service.redaction import redact_payload  # noqa: E402

FIXTURE_PATH = SERVICE_ROOT / "evaluation" / "redaction-cases.jsonl"


def load_cases() -> list[dict[str, Any]]:
    cases: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    for line_number, line in enumerate(FIXTURE_PATH.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            case = json.loads(line)
        except json.JSONDecodeError as error:
            raise ValueError(f"Invalid JSON on fixture line {line_number}") from error
        if not isinstance(case, dict):
            raise ValueError(f"Fixture line {line_number} must be a JSON object")
        if case.get("group") not in {"supported", "benign", "known_gap"}:
            raise ValueError(f"Invalid fixture group on line {line_number}")
        case_id = case.get("id")
        if not isinstance(case_id, str) or not case_id or case_id in seen_ids:
            raise ValueError(f"Fixture line {line_number} has a missing or duplicate ID")
        if not isinstance(case.get("text"), str) or not case["text"]:
            raise ValueError(f"Fixture {case_id} needs non-empty text")
        sensitive_value = case.get("sensitive_value")
        if case["group"] in {"supported", "known_gap"} and (
            not isinstance(sensitive_value, str)
            or not sensitive_value
            or sensitive_value not in case["text"]
        ):
            raise ValueError(f"Fixture {case_id} needs a sensitive value")
        seen_ids.add(case_id)
        cases.append(case)
    if not cases:
        raise ValueError("No redaction fixtures were found")
    return cases


def evaluate(cases: list[dict[str, Any]]) -> dict[str, Any]:
    counts: Counter[str] = Counter()
    missed_ids: list[str] = []
    changed_benign_ids: list[str] = []
    detected_gap_ids: list[str] = []
    unexpected_gap_ids: list[str] = []

    for case in cases:
        text = case["text"]
        redacted, _ = redact_payload({"text": text})
        output = redacted["text"]
        changed = output != text
        group = case["group"]
        counts[f"{group}_cases"] += 1

        if group == "supported":
            secret_removed = case["sensitive_value"] not in output
            if changed and secret_removed:
                counts["supported_detected"] += 1
            else:
                counts["supported_missed"] += 1
                missed_ids.append(case["id"])
        elif group == "benign":
            if changed:
                counts["benign_changed"] += 1
                changed_benign_ids.append(case["id"])
            else:
                counts["benign_unchanged"] += 1
        elif changed:
            counts["known_gaps_detected"] += 1
            detected_gap_ids.append(case["id"])
            unexpected_gap_ids.append(case["id"])
        else:
            counts["known_gaps_not_detected"] += 1

    supported_total = counts["supported_cases"]
    benign_total = counts["benign_cases"]
    return {
        "fixtureCount": len(cases),
        "supportedPatterns": {
            "cases": supported_total,
            "detected": counts["supported_detected"],
            "missed": counts["supported_missed"],
            "recall": counts["supported_detected"] / supported_total if supported_total else 1.0,
            "missedCaseIds": missed_ids,
        },
        "benignText": {
            "cases": benign_total,
            "unchanged": counts["benign_unchanged"],
            "changed": counts["benign_changed"],
            "specificity": counts["benign_unchanged"] / benign_total if benign_total else 1.0,
            "changedCaseIds": changed_benign_ids,
        },
        "knownBlindSpots": {
            "cases": counts["known_gap_cases"],
            "detected": counts["known_gaps_detected"],
            "notDetected": counts["known_gaps_not_detected"],
            "caseIdsDetected": detected_gap_ids,
            "unexpectedResultCaseIds": unexpected_gap_ids,
        },
        "limitations": (
            "Synthetic fixtures measure only listed patterns; they do not estimate "
            "real-world secret-detection coverage."
        ),
    }


def main() -> int:
    report = evaluate(load_cases())
    print(json.dumps(report, indent=2, sort_keys=True))
    supported = report["supportedPatterns"]
    benign = report["benignText"]
    blind_spots = report["knownBlindSpots"]
    return (
        0
        if supported["missed"] == 0
        and benign["changed"] == 0
        and not blind_spots["unexpectedResultCaseIds"]
        else 1
    )


if __name__ == "__main__":
    raise SystemExit(main())
