# Local API observability

AURA can optionally run Prometheus and Alertmanager beside the Compose stack. Spring Boot Actuator exposes only health and Prometheus metrics on a separate management port. The API binds that port to the loopback interface by default; Compose overrides the bind address so Prometheus can reach it over a dedicated internal network. Port 8081 is not published to the host.

## Start and inspect

Start the optional monitoring profile from the repository root:

```powershell
docker compose --profile observability up --build -d
```

Open Prometheus at <http://127.0.0.1:9090>. In **Status → Targets**, `aura-api` should be `UP`. Useful initial queries include:

```promql
up{job="aura-api"}
rate(http_server_requests_seconds_count[5m])
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m])))
```

Alertmanager is at <http://127.0.0.1:9093>. Prometheus loads the rules in `ops/prometheus/rules/aura.yml` and forwards firing alerts to it. The default Alertmanager receiver is silent; email notifications require the explicit opt-in steps in the [alerting runbook](ALERTING.md).

When running Spring directly on the host with the default settings, check `http://127.0.0.1:8081/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness`. In Compose, port 8081 is not published to the host; use the Prometheus target status to confirm the scrape. Health details are hidden. The health endpoint reports component status; liveness/readiness groups provide process state for a future orchestrator.

Stop the stack with `docker compose down`. Prometheus samples remain in the `aura-prometheus-data` volume. `docker compose down -v` also deletes that history and the PostgreSQL volume.

## Boundaries and limits

- Only the `health` and `prometheus` endpoints are exposed, and both are read-only. Other Actuator endpoints are disabled and denied by Spring Security.
- The management port has no host port mapping. In Compose, the API, Prometheus, and Alertmanager containers join the dedicated internal `observability` network. Prometheus and Alertmanager UIs bind to host loopback at ports 9090 and 9093 and have no application login; do not publish or port-forward them.
- Metrics cover framework HTTP/JVM/process measurements. AURA does not add research queries, source text, document contents, CVEs, or user identifiers as metric labels.
- Prometheus stores samples in a local Docker volume with 15-day and 1 GB retention limits. Alertmanager state uses a separate local volume. Neither volume is included in PostgreSQL backups or replicated; losing them removes monitoring history and alert silences, not AURA research data.
- The tracked Alertmanager configuration sends no notifications. An optional email overlay gives Alertmanager outbound network access; SMTP delivery has not been verified without an operator's relay configuration. Distributed tracing, durable remote storage, and a production identity/firewall policy remain out of scope.
- Compose applies container memory ceilings for the 16 GB local workstation target. They are guardrails, not sizing evidence from a load test.

Spring Boot requires explicit endpoint exposure and recommends protecting exposed endpoints with security or network controls. See the [Actuator endpoint security documentation](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html) and [metrics documentation](https://docs.spring.io/spring-boot/reference/actuator/metrics.html). Prometheus retention and local-storage limitations are described in its [storage documentation](https://prometheus.io/docs/prometheus/latest/storage/).
