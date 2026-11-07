# AURA AI product requirements

## Product summary

AURA AI is a personal research and intelligence workspace. It searches configured public web and news sources, with direct social sources added only through permitted official APIs; organizes user-selected documents and evidence; answers questions with traceable citations; and provides a defensive cybersecurity intelligence workflow. Search coverage is always bounded by each provider's index and access rules, not a promise to search every internet page or post.

AURA is not a chatbot that treats generated text as evidence. It separates the user's request, retrieved source evidence, model interpretation, findings, uncertainty, and recommendations.

## Users and use cases

Initial user: one authorized owner operating the app locally. Future multi-user support is a design consideration, not an MVP feature.

1. Research a public-interest subject, topic, organization, country, technology, or event.
2. Filter and compare sourced results, inspect coverage and conflicts, and export a report.
3. Import selected documents, search the personal knowledge base, and ask questions grounded in those sources.
4. Analyze an SBOM or vulnerability report, enrich matches with public data, and understand prioritization.
5. Review sources, tool operations, model interpretation, and approvals associated with a report.

## Product requirements

### Research intelligence

- Accept a subject and optional date, source, language, region, and keyword filters.
- Search one configured provider or all enabled public providers available through permitted interfaces. Direct social connectors require a separate official-API and data-lifecycle review.
- Display providers queried, query time, result counts, date range, and coverage limitations.
- Fetch selected public pages with destination, redirect, size, and time limits.
- Preserve canonical URL, publisher/author metadata where permitted, published time, retrieval time, and content hash.
- Deduplicate repeated coverage while preserving all source references.
- Distinguish source claims from AURA synthesis; expose disagreement and weak corroboration.
- Produce Markdown/PDF reports and CSV/JSON exports subject to source terms.

### Knowledge and document intelligence

- Import selected PDF, text, Markdown, HTML, CSV, and JSON files.
- Record document metadata, version, source, parser version, and access owner.
- Keep parsing, normalization, chunking, indexing, retrieval, and synthesis separately observable.
- Begin with full-text retrieval. Add embeddings only when evaluation justifies them.
- Attach document and passage citations to grounded answers.
- Treat imported content as untrusted data, not instructions.

### Research workflow

- Persist a research request and workflow state.
- Use a deterministic workflow for validation, search, fetch, extraction, deduplication, evidence storage, synthesis, and reporting.
- Use models only for bounded planning, extraction, or synthesis.
- Validate each tool operation and argument with application policy.
- Record retries, errors, approvals, and outputs.

### Cybersecurity intelligence

- Integrate NVD CVE data, CISA KEV, FIRST EPSS, vendor advisories, and selected SBOM/report formats.
- Preserve source-provided severity and exploitation indicators separately.
- Match inventory evidence to affected products/versions with confidence.
- Prioritize with transparent rules and show the input factors.
- Use LLMs to explain evidence, not invent severity or exploitation facts.
- Keep initial scope defensive and informational.

### Workspace

Dashboard and new research flow; history and detail; evidence explorer; document library and knowledge search; grounded AI chat; cybersecurity findings; reports, settings, and audit history.

## Person-focused research boundary

Support public-interest research and public statements. Require disambiguation when a name could refer to multiple people. Do not aggregate private contact details, home addresses, precise live location, credentials, sensitive personal traits, or private-account content. Do not enable stalking, harassment, targeting, or bulk personal profiling.

## Non-functional requirements

- Local-first and suitable for a solo developer's Windows machine with 16 GB RAM.
- No paid model provider is required for the base application.
- Important claims retain source references and timestamps.
- Secrets never enter source control, model prompts, reports, or ordinary logs.
- Every connector has narrow scope and revocation.
- API, database, and AI operations use timeouts and bounded inputs.
- Reproducible setup, migrations, useful tests, and maintained threat model.

## Success measures

Citation coverage/correctness; source diversity and recall for curated queries; duplicate and contradiction review quality; document retrieval quality; CVE/product match precision; workflow success/failure, latency, retries, model tokens/cost, and policy denials.
