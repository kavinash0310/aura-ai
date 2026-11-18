# Testing and verification guide

This guide lists the deterministic checks available in the repository. CI runs the Java, Python, web, and Compose groups for each push and pull request. A green build verifies the listed code and configuration checks; it does not prove public-source coverage, model factuality, production availability, or deployment security.

## Local prerequisites

- JDK 21 and Maven
- Python 3.12 and `uv`
- Node.js 22 and npm
- Docker Desktop with the Compose plugin for container and monitoring checks

## Backend

From the repository root:

```powershell
mvn -B -ntp -f backend/pom.xml verify
```

This runs the Spring API's unit/integration tests and packages the application. PostgreSQL integration tests that require Docker may be skipped when no Docker engine is available; check the Maven test summary rather than treating a skipped test as coverage.

## AI service

```powershell
Set-Location ai-service
uv sync --locked --all-groups
uv run pytest
uv run python scripts/evaluate_redaction.py
uv run python scripts/evaluate_reports.py
uv run ruff check src tests scripts
Set-Location ..
```

The redaction and report evaluators use synthetic fixtures. They check only listed patterns, schema, selected-source membership, and exact quote anchors; they do not measure factual accuracy or semantic evidence support.

## Web workspace

```powershell
Set-Location web
npm ci
npm test
npm run build
Set-Location ..
```

The build includes TypeScript checking and the production bundle.

## Compose and observability configuration

The repository bootstrap script creates the local `.env` without overwriting an existing file. With that file present:

```powershell
docker compose config --quiet
docker compose --profile observability config --quiet
docker compose -f compose.yaml -f compose.email-alerts.yaml --profile observability config --quiet
```

CI then runs the matching pinned Prometheus and Alertmanager tool images to check the Prometheus scrape config, rule expressions, and both Alertmanager YAML files. Its SMTP password is a temporary test placeholder, never a real credential. Compose configuration checks do not start containers or contact the configured SMTP relay.

## End-to-end manual checks

Use the walkthroughs in [demo scenarios](DEMO-SCENARIOS.md) after running the stack. For monitoring, verify that the API target is `UP`, then inspect the Prometheus **Alerts** page and Alertmanager UI. A successful local setup should keep all host ports bound to `127.0.0.1`.

## Coverage that remains manual

- A real SMTP delivery check needs a relay credential and is intentionally not part of CI.
- Backup recovery against a disposable PostgreSQL database is documented in the [backup/restore runbook](DATABASE-BACKUP-RESTORE.md).
- Model quality needs human-reviewed examples; schema and citation membership checks are not semantic faithfulness checks.
- Remote deployment, multiple users, data isolation, and production abuse controls have not been tested or approved.
