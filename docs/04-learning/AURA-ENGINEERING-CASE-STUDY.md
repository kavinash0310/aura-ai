# AURA AI engineering case study

This is a portfolio-ready technical case study outline for the current repository. Fill the **owner reflection** section after running and explaining the system yourself; generated implementation is not evidence that the owner has mastered it.

## Problem

AURA is a single-owner research workspace for configured public sources, selected local documents, and defensive cybersecurity evidence. It preserves source IDs, provider/query provenance, timestamps, and explicit action boundaries so model interpretation can be reviewed beside its inputs.

## Architecture

- **React and TypeScript** provide the local research and document workspace.
- **Spring Boot and PostgreSQL** own authentication, validation, job state, durable evidence, migrations, and provider access.
- **FastAPI** handles bounded model-facing transformations and has no database or public-source adapter access.
- **Ollama** is an optional local model endpoint; model actions are explicit, schema checked, and have no hosted fallback.
- **Docker Compose** serves the single-owner development deployment with loopback-bound host ports.

The UI calls Spring; Spring validates the owner action and persists job/evidence state; selected providers return untrusted results; Spring normalizes evidence and stores provenance; an explicit report, comparison, or document-answer request may send only selected bounded text to the local model; output is checked before display. Search, document Q&A, and vulnerability lookups are separate flows with separate limits.

## Engineering decisions

1. Keep the main domain in Spring because identity, transactions, persistence, and the owner's established Java experience are central. Isolate Python to AI workloads that benefit from its ecosystem.
2. Use PostgreSQL full-text and exact-phrase matching first. Semantic retrieval and pgvector need a measured evaluation case before adding complexity.
3. Use explicit provider calls and bounded deterministic workflows. Avoid general-purpose autonomous agents and unrestricted tools.
4. Treat model output as interpretation. Citation membership and quote matching prove limited structural properties, not that a source supports a claim.
5. Keep operations local by default. Metrics, alert rules, and dashboards are loopback-only; email alerting is separately opt-in because it adds secret storage and outbound egress.

## Verification evidence

The repository CI workflow checks Java tests/package verification, AI-service tests and synthetic evaluators, Python style, web tests/build, Compose configuration, Prometheus configuration/rules, and Alertmanager configuration. See the [testing guide](../19-operations/TESTING.md) for local commands and what each check does not prove.

The synthetic evaluations cover listed credential patterns and deterministic report schema/citation/quote behavior. They do not measure real-world secret coverage, semantic faithfulness, source truth, or model quality. Alert configuration parsing does not prove SMTP delivery or useful alert thresholds.

## Deliberate limits

- Configured search providers return bounded indexed results; AURA does not search every internet page, read private social accounts, or promise completeness.
- Social connectors currently include public Bluesky posts; there is no X/Twitter account connector.
- Document retrieval is bounded exact-phrase search with deterministic context windows, not semantic or hybrid retrieval; scanned PDF OCR is not enabled.
- Vulnerability analysis uses declared CVEs and selected public advisories. It does not discover assets, scan hosts, or execute exploits.
- The local deployment uses development Basic Auth and is not ready for remote or multi-user use. TLS, a production identity model, tenant isolation, process isolation for untrusted PDFs, rate limits, and a deployment-specific review are still required.
- Distributed tracing, remote metrics storage, host-loss recovery, and operational email delivery have not been verified here.

## Owner reflection — complete after the walkthrough

Write a short answer in your own words for each prompt:

1. Which request crosses the Java/Python boundary, and what prevents Python from becoming the authority for permissions?
2. Choose one research result and explain how its provider, query, timestamps, and evidence ID survive through an export.
3. Explain one database migration and the failure it prevents.
4. What does the document citation prove, and what does it not prove?
5. How is a vulnerable SBOM component identified, and which comparisons are deliberately `NOT_ASSESSED`?
6. What changes in the Compose network when email alerting is enabled?
7. Which production risk would you address first if this moved beyond one local owner, and why?

Do not mark the learning tracker complete until you can answer these without reading generated explanations and can make one small independent code or configuration change.
