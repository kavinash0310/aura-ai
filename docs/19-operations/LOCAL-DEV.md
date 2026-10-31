# Local development

## Requirements

- Windows 10/11 with PowerShell.
- JDK 21.
- Docker Desktop with Compose.
- Maven 3.9 or later for local Java builds.
- Python 3.12 or later and uv 0.13.6 for local AI-service tests.
- Git.

## Configuration and startup

1. Copy .env.example to .env.
2. Replace placeholder DB and API passwords with unique random values of at least 24 characters.
3. Keep .env untracked; never paste actual secrets into issues or logs.
4. Start with docker compose up --build.
5. Spring API binds to 127.0.0.1:8080. GET /api/v1/health is public; other routes use local HTTP Basic credentials.
6. AI service binds to 127.0.0.1:8001. GET /api/v1/health is public and reports service identity only; model inference and provider access are not implemented.
7. Stop with docker compose down. Use docker compose down -v only when you intend to delete the local database volume.
8. Verify Java with mvn -f backend/pom.xml verify.
9. For Python checks, change into ai-service and run uv sync --locked --all-groups, uv run pytest, and uv run ruff check src tests.

## Useful commands

- Service status: docker compose ps
- API logs: docker compose logs -f api
- AI-service logs: docker compose logs -f ai
- Database logs: docker compose logs -f db

## Limits

Basic Auth is development-only. Do not port-forward or publish the services. M2 establishes an internal service boundary only; it has no model, data connector, database access, or outbound research behavior.

