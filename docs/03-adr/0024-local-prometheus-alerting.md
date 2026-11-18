# ADR 0024: Local Prometheus alerting with opt-in email egress

- Status: Accepted
- Date: 2026-11-18

## Context

AURA already has an optional Prometheus profile with health and metrics on an internal Compose network. Its backlog calls for alert delivery, but the initial product is a single-owner local application on a 16 GB Windows workstation. Monitoring should help the owner diagnose the API without silently sending operational information to an external service.

## Problem

Prometheus can expose metric graphs but does not provide the desired grouped alert view or notification routing. Email can be useful, but it introduces a credential and an outbound network path that should not be part of the default local setup.

## Options considered

1. Add Alertmanager with a silent default receiver and an explicit email overlay.
2. Configure an email receiver in the default monitoring profile.
3. Add an external hosted monitoring provider.
4. Defer alerting and keep metrics-only monitoring.

## Decision

Use pinned local Prometheus and Alertmanager containers in the optional `observability` profile. Add bounded rules for API target availability, sustained 5xx ratio, p95 latency, and JVM heap. The tracked Alertmanager config routes to a receiver with no notification integration. Email is an opt-in Compose overlay that reads an ignored password file, requires SMTP TLS, and attaches only Alertmanager to an outbound-capable network.

Prometheus local retention is capped by time and size. Compose memory limits provide initial guardrails for the owner's 16 GB workstation. Configuration syntax is checked in CI. These limits and thresholds are starting values, not production SLIs or load-test results.

## Consequences

- Local monitoring works without an email credential or notification egress.
- Email is possible without editing the base Compose network, but the owner must protect the local secret and verify delivery.
- Alert rules are intentionally limited to the API metrics currently exposed. PostgreSQL, Python, host disk, model service, distributed tracing, and remote retention are not covered.
- Loopback UIs have no login and must remain local. A remote deployment needs its own identity, TLS, network, retention, and alerting review.
- CI proves configuration parses; it cannot prove the relay works or thresholds are useful.

## Reconsider when

AURA gains a deployment environment, multiple operators, a measured service objective, more monitored components, or an approved remote telemetry provider.

## Learning and interview prompts

- Explain the difference between metrics, alert evaluation, grouping, and notification delivery.
- Why is an empty notification receiver safer for local-first defaults?
- Which data and network boundary changes when the email overlay is enabled?
- How would you derive thresholds from baseline data instead of guessing?
