# Local API observability

AURA can optionally run Prometheus beside the Compose stack. Spring Boot Actuator exposes only health and Prometheus metrics on a separate management port. The API binds that port to the loopback interface by default; Compose overrides the bind address so Prometheus can reach it over a dedicated internal network. Port 8081 is not published to the host.

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

When running Spring directly on the host with the default settings, check `http://127.0.0.1:8081/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness`. In Compose, port 8081 is not published to the host; use the Prometheus target status to confirm the scrape. Health details are hidden. The health endpoint reports component status; liveness/readiness groups provide process state for a future orchestrator.

Stop the stack with `docker compose down`. Prometheus samples remain in the `aura-prometheus-data` volume. `docker compose down -v` also deletes that history and the PostgreSQL volume.

## Boundaries and limits

- Only the `health` and `prometheus` endpoints are exposed, and both are read-only. Other Actuator endpoints are disabled and denied by Spring Security.
- The management port has no host port mapping. In Compose, only the API and Prometheus containers join the dedicated internal `observability` network. Prometheus's web UI binds to host loopback at port 9090 and has no application login; do not publish or port-forward it.
- Metrics cover framework HTTP/JVM/process measurements. AURA does not add research queries, source text, document contents, CVEs, or user identifiers as metric labels.
- Prometheus stores samples in a local Docker volume with its default 15-day retention. The volume is not included in PostgreSQL backups and is not replicated; losing it removes monitoring history, not AURA research data.
- This is a single-instance local monitoring setup. It does not provide alert delivery, distributed tracing, durable remote storage, or a production identity/firewall policy. Restrict the management network and add authenticated transport before reusing it in a deployment where other workloads are not trusted.

Spring Boot requires explicit endpoint exposure and recommends protecting exposed endpoints with security or network controls. See the [Actuator endpoint security documentation](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html) and [metrics documentation](https://docs.spring.io/spring-boot/reference/actuator/metrics.html). Prometheus retention and local-storage limitations are described in its [storage documentation](https://prometheus.io/docs/prometheus/latest/storage/).
