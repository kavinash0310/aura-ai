# Local development

## Requirements

- Windows 10/11 with PowerShell.
- JDK 21.
- Docker Desktop with Compose.
- Maven 3.9 or later for local Java builds.
- Python 3.12 or later and uv 0.13.6 for local AI-service tests.
- Node.js 22 if developing the web workspace outside its container.
- Git.

## Configuration and startup

1. Copy .env.example to .env.
2. Replace placeholder DB and API passwords with unique random values of at least 24 characters.
3. Keep .env untracked; never paste actual secrets into issues or logs.
4. To enable search, add your own `AURA_BRAVE_API_KEY` to `.env`. Leave it blank to keep the provider disabled. A search sends the query to Brave under your provider account and may be subject to its quotas or charges.
5. `AURA_NVD_API_KEY` is optional. The workspace requests one exact CVE from NVD only after you click “Load NVD details”; the optional key is sent in a header. No request runs automatically. The NVD response is cached in memory for six hours, up to 500 CVEs, and is not persisted.
6. Start with `docker compose up --build`.
7. Open the workspace at 127.0.0.1:3000. Enter the local Basic Auth credentials from `.env`; they remain in page memory and clear on reload or disconnect. Recent briefs can be reopened from the sidebar; deleting a brief removes its saved source rows.
8. Spring API binds to 127.0.0.1:8080. GET `/api/v1/health` is public; other routes use local HTTP Basic credentials. The web container proxies `/api` requests to Spring on the internal Compose network.
9. AI service binds to 127.0.0.1:8001. GET `/api/v1/health` is public and reports service identity only; model inference and provider access are not implemented.
10. Direct API: create a research job, explicitly call its `/search` route, then use `/sources` filters or `/export?format=json|csv|md`. See the API contract for examples and limits.
11. The workspace's Cyber intelligence section accepts one to 25 exact CVE IDs or a CycloneDX JSON SBOM up to 2 MB. For an SBOM, AURA resolves declared component references and enriches only CVE IDs already listed in the file; it does not infer package vulnerabilities. AURA downloads the CISA KEV catalog and sends only those CVE IDs to FIRST EPSS. Click “Load NVD details” on a single CVE to request its bounded NVD record; the SBOM file itself is never sent to NVD. Results can be downloaded as JSON or CSV; uploads and lookup results are not stored. The CISA and NVD caches are memory-only and last six hours; NVD retains at most 500 records. Only the Brave search key and optional NVD key are needed for their respective provider actions.
12. In Documents, import UTF-8 `.txt` or `.md` files up to 1 MB. Search uses PostgreSQL's local full-text index plus an exact-phrase check; previews include character-offset citations. Documents are stored in PostgreSQL, are not sent to external providers or models, and can be explicitly downloaded or deleted.
13. Stop with `docker compose down`. Use `docker compose down -v` only when you intend to delete the local database volume.
14. Verify Java with `mvn -f backend/pom.xml verify`.
15. For frontend checks, change to `web` and run `npm ci`, `npm test`, then `npm run build`.
16. For Python checks, change into `ai-service` and run `uv sync --locked --all-groups`, `uv run pytest`, and `uv run ruff check src tests`.

## Useful commands

- Service status: docker compose ps
- API logs: docker compose logs -f api
- AI-service logs: docker compose logs -f ai
- Database logs: docker compose logs -f db

## Limits

Basic Auth is development-only. Do not port-forward or publish the services. The Java API sends an explicit search query to the configured Brave provider; it does not fetch result URLs. Explicit vulnerability lookups read the CISA KEV catalog and send only submitted CVE IDs to FIRST EPSS; NVD receives one exact CVE only after a separate per-item click. SBOM uploads are bounded to 2 MB, parsed in memory, and not persisted. Research documents are stored in PostgreSQL after explicit upload and remain local to this instance. These are public prioritization signals and do not establish whether your installed assets are affected. The Python service remains isolated and has no model, data connector, database access, or outbound research behavior.
