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
2. Replace placeholder DB, API, and internal AI service secrets with separate unique random values of at least 24 characters.
3. Keep .env untracked; never paste actual secrets into issues or logs.
4. Public GDELT news and Bluesky post search are enabled by default and require no key or account credentials. Add your own `AURA_BRAVE_API_KEY` to `.env` for Brave's web/news/discussion search; Automatic uses one provider (Brave first, then GDELT, then Bluesky), while All configured sources sends the query to each enabled provider. Set `AURA_GDELT_ENABLED=false` or `AURA_BLUESKY_ENABLED=false` to disable either provider. Only an explicitly started search sends its query; Brave may have quotas or charges under the owner's account. If one provider fails in all mode, the job fails and no source rows are saved. Bluesky search returns public posts and does not apply the country filter.
5. `AURA_NVD_API_KEY` is optional. The workspace requests one exact CVE from NVD only after you click “Load NVD details”; the optional key is sent in a header. No request runs automatically. The NVD response is cached in memory for six hours, up to 500 CVEs, and is not persisted.
6. Start with `docker compose up --build`.
7. Open the workspace at 127.0.0.1:3000. Enter the local Basic Auth credentials from `.env`; they remain in page memory and clear on reload or disconnect. Recent briefs can be reopened from the sidebar; deleting a brief removes its saved source rows.
8. Spring API binds to 127.0.0.1:8080. GET `/api/v1/health` is public; other routes use local HTTP Basic credentials. The web container proxies `/api` requests to Spring on the internal Compose network.
9. AI service binds to 127.0.0.1:8001. GET `/api/v1/health` is public and reports service identity only. The report route is internal-token protected; it is disabled unless the local Ollama provider is configured.
10. Optional local report setup: install Ollama and at least one local model; set `AURA_AI_MODEL_PROVIDER=ollama`, `AURA_AI_OLLAMA_MODEL` to the installed name, and `AURA_AI_OLLAMA_BASE_URL` to the local Ollama origin. Generate a separate random `AURA_AI_INTERNAL_TOKEN`. Keep the model endpoint reachable only from trusted local services. No request goes to Ollama until the owner clicks Report. AURA sends the selected query, titles, URLs, and provider snippets for up to 20 visible sources; it does not send document-library text or fetch the linked pages.
11. Direct API: create a research job, explicitly call its `/search` route, then use `/sources` filters, `/report` with selected source IDs, or `/export?format=json|csv|md`. See the API contract for examples and limits.
12. The workspace's Cyber intelligence section accepts one to 25 exact CVE IDs or a CycloneDX JSON SBOM up to 2 MB. For an SBOM, AURA resolves declared component references and enriches only CVE IDs already listed in the file; it does not infer package vulnerabilities. AURA downloads the CISA KEV catalog and sends only those CVE IDs to FIRST EPSS. Click “Load NVD details” on a single CVE to request its bounded NVD record; the SBOM file itself is never sent to NVD. Results can be downloaded as JSON or CSV; uploads and lookup results are not stored. The CISA and NVD caches are memory-only and last six hours; NVD retains at most 500 records. A Brave search key is optional, and an NVD key is optional for the NVD provider action.
13. In Documents, import UTF-8 `.txt` or `.md` files up to 1 MB. Search uses PostgreSQL's local full-text index plus an exact-phrase check; previews include character-offset citations. Documents are stored in PostgreSQL, are not sent to external providers or models, and can be explicitly downloaded or deleted.
14. Stop with `docker compose down`. Use `docker compose down -v` only when you intend to delete the local database volume.
15. Verify Java with `mvn -f backend/pom.xml verify`.
16. For frontend checks, change to `web` and run `npm ci`, `npm test`, then `npm run build`.
17. For Python checks, change into `ai-service` and run `uv sync --locked --all-groups`, `uv run pytest`, and `uv run ruff check src tests`.

## Useful commands

- Service status: docker compose ps
- API logs: docker compose logs -f api
- AI-service logs: docker compose logs -f ai
- Database logs: docker compose logs -f db

## Limits

Basic Auth is development-only. Do not port-forward or publish the services. The Java API sends an explicit search query to the selected Brave, GDELT, and/or Bluesky provider(s); it does not fetch result URLs. GDELT searches its indexed public news over at most three months. Bluesky search uses the official public AppView and returns bounded public posts without account or private-feed access; it does not apply country filters. Saved results include provider provenance and can be filtered by provider. A report action sends only the selected saved result metadata and snippets to the explicitly configured local Ollama endpoint; it never falls back to a hosted model. Explicit vulnerability lookups read the CISA KEV catalog and send only submitted CVE IDs to FIRST EPSS; NVD receives exact CVE IDs only after a separate per-item or bounded batch click. SBOM uploads are bounded to 2 MB, parsed in memory, and not persisted. Research documents are stored in PostgreSQL after explicit upload and remain local to this instance. Report citations are checked for source-ID membership but not semantic support. Vulnerability data does not establish whether installed assets are affected.
