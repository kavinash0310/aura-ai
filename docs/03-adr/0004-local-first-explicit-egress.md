# ADR 0004: Local-first and explicit model egress

- Status: Accepted
- Date: 2026-10-31

## Context
Research and documents may be sensitive. The owner prefers free/open-source technology and has Windows with 16 GB RAM. Hosted models help but send data externally.

## Decision
Develop with Docker Compose and bind to loopback. Keep local models optional and hosted models disabled until configured. Local-only mode fails visibly when its provider is unavailable. Never send credentials, entire connected accounts, or uploaded document-library text to a model. The first local report feature sends only selected public search-result metadata/snippets after an explicit action.

## Trade-offs
Local models need memory, disk, and updates and can be slower/weaker. Hosted models add cost, network dependence, and egress. Provider abstraction supports either without silent fallback.

## Consequences
Show provider and evidence categories for hosted requests. Track model and prompt versions. Redact secrets. Remote deployment needs separate auth/TLS/retention/abuse design.

## Reconsider when
A product requirement justifies hosted inference or remote collaboration with explicit privacy review.
