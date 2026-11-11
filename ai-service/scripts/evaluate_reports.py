"""Check synthetic report fixtures for schema and deterministic citation regressions."""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any

from pydantic import ValidationError

SERVICE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE_ROOT / "src"))

from aura_ai_service.main import (  # noqa: E402
    ReportSource,
    ResearchReportContent,
    _report_quotes_match_sources,
)

FIXTURE_PATH = SERVICE_ROOT / "evaluation" / "research-report-cases.jsonl"
SEMANTIC_REVIEW_LABELS = {"SUPPORTED", "CONTRADICTED", "UNREVIEWED"}


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
        case_id = case.get("id")
        if not isinstance(case_id, str) or not case_id or case_id in seen_ids:
            raise ValueError(f"Fixture line {line_number} has a missing or duplicate ID")
        if not isinstance(case.get("sources"), list) or not isinstance(
            case.get("candidate"), dict
        ):
            raise ValueError(f"Fixture {case_id} needs sources and a candidate report")
        expected = case.get("expected")
        if not isinstance(expected, dict):
            raise ValueError(f"Fixture {case_id} needs expected checks")
        semantic_label = expected.get("semanticReview")
        if semantic_label not in SEMANTIC_REVIEW_LABELS:
            raise ValueError(f"Fixture {case_id} has an unsupported semantic-review label")
        seen_ids.add(case_id)
        cases.append(case)
    if not cases:
        raise ValueError("No report-quality fixtures were found")
    return cases


def evaluate(cases: list[dict[str, Any]]) -> dict[str, Any]:
    values: dict[str, list[bool | None]] = {
        "schemaValid": [],
        "citationsValid": [],
        "quotesMatch": [],
    }
    semantic_labels: Counter[str] = Counter()
    mismatches: list[dict[str, str]] = []
    case_results: list[dict[str, Any]] = []

    for case in cases:
        case_id = case["id"]
        sources = [ReportSource.model_validate(source) for source in case["sources"]]
        try:
            report = ResearchReportContent.model_validate(case["candidate"])
        except ValidationError:
            actual: dict[str, bool | None] = {
                "schemaValid": False,
                "citationsValid": None,
                "quotesMatch": None,
            }
        else:
            source_ids = {source.id for source in sources}
            referenced_ids = set(report.summary_source_ids)
            for finding in report.findings:
                referenced_ids.update(finding.source_ids)
            actual = {
                "schemaValid": True,
                "citationsValid": referenced_ids.issubset(source_ids),
                "quotesMatch": _report_quotes_match_sources(report, sources),
            }

        expected = case["expected"]
        for check_name, check_value in actual.items():
            values[check_name].append(check_value)
            if expected.get(check_name) is not check_value:
                mismatches.append({"caseId": case_id, "check": check_name})
        semantic_labels[expected["semanticReview"]] += 1
        case_results.append(
            {
                "caseId": case_id,
                **actual,
                "semanticReviewLabel": expected["semanticReview"],
            }
        )

    def metric_summary(check_values: list[bool | None]) -> dict[str, int]:
        return {
            "passed": sum(value is True for value in check_values),
            "failed": sum(value is False for value in check_values),
            "notEvaluated": sum(value is None for value in check_values),
        }

    return {
        "fixtureCount": len(cases),
        "deterministicChecks": {
            name: metric_summary(check_values) for name, check_values in values.items()
        },
        "cases": case_results,
        "semanticReviewLabels": dict(sorted(semantic_labels.items())),
        "semanticSupportAutomaticallyScored": False,
        "expectationMismatches": mismatches,
        "limitations": (
            "Synthetic cases verify schema, citation membership, and exact quote matching only. "
            "Semantic labels are illustrative fixture annotations, not a model-quality score."
        ),
    }


def main() -> int:
    report = evaluate(load_cases())
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0 if not report["expectationMismatches"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
