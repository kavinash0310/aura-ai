# Local development

## Requirements

- Windows 10/11 with PowerShell.
- JDK 21.
- Docker Desktop with Compose.
- Maven 3.9 or later for local source runs.
- Git.

## Configuration and startup

1. Copy .env.example to .env.
2. Replace placeholder DB and API passwords with unique random values of at least 24 characters.
3. Keep .env untracked; never paste actual secrets into issues or logs.
4. Start with docker compose up --build.
5. API binds to 127.0.0.1:8080. GET /api/v1/health is public; other routes use local HTTP Basic credentials.
6. Stop with docker compose down. Use docker compose down -v only when you intend to delete the local database volume.
7. Verify backend with mvn -f backend/pom.xml verify.

## Useful commands

- Service status: docker compose ps
- API logs: docker compose logs -f api
- Database logs: docker compose logs -f db

## Limits

Basic Auth is development-only. Do not port-forward or publish the service. Hosted model support and remote deployment are not enabled in M1. Current research job endpoint records a request only.
