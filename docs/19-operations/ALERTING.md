# Local alerting

AURA's optional local observability profile evaluates a small set of Prometheus rules and sends firing alerts to Alertmanager. The default Alertmanager receiver is intentionally silent: enabling local monitoring does not send information off the machine. Alert and dashboard ports bind to loopback and should not be port-forwarded.

## Start the local dashboards

From the repository root, start the stack with its monitoring profile:

```powershell
docker compose --profile observability up --build -d
```

- Prometheus: <http://127.0.0.1:9090>. Check **Status → Targets** for `aura-api` and **Alerts** for rule state.
- Alertmanager: <http://127.0.0.1:9093>. Check grouped alerts and silences.

The configured rules cover the API metrics target being unavailable, a sustained 5xx ratio above 5%, a p95 request duration above two seconds, and JVM heap usage above 85%. Error-rate and latency rules require at least 0.1 requests per second (about 30 requests in five minutes) to avoid treating a few local requests as a stable service signal. The rules use five to ten minute windows to reduce short-lived noise. These are starter thresholds, not service-level objectives; adjust them after observing normal local use. The current rules monitor the Spring API only. They do not monitor the Python service, PostgreSQL, host disk, or the local model.

Prometheus retains at most 15 days or 1 GB of local samples. Alertmanager state uses its own Docker volume. Neither volume is included in PostgreSQL backups. `docker compose down` keeps volumes; `docker compose down -v` deletes them.

## Opt in to email

Email sends alert metadata outside the computer. Enable it only when you want that egress and have configured a trusted SMTP relay.

1. Copy the example and replace its `.invalid` SMTP host, sender, username, and destination with values from your provider:

   ```powershell
   Copy-Item ops/alertmanager/alertmanager.email.example.yml ops/alertmanager/alertmanager.local.yml
   ```

2. Create `ops/alertmanager/smtp-password` with a local editor. Store only the relay credential in this ignored file; do not paste it into a command, commit, issue, or chat. Restrict its filesystem access to your Windows account.
3. Start the email overlay, which adds only Alertmanager to a normal Docker bridge network for SMTP egress:

   ```powershell
   docker compose -f compose.yaml -f compose.email-alerts.yaml --profile observability up --build -d
   ```

4. Check Alertmanager's **Status → Configuration** page and its logs, then verify delivery with an alert you can safely trigger in a disposable local setup. Remove the overlay and local credential when email is no longer needed.

The tracked example requires TLS and reads the password from a file. It contains reserved `.invalid` addresses and cannot deliver mail until you configure it. The SMTP relay's availability, authentication policy, and message handling are outside AURA's control. Do not route alerts to a public list: alert labels and summaries disclose operational state.

## Configuration checks

The CI workflow checks the base and email Compose overlays, Prometheus configuration and rules, and both Alertmanager configurations. To run the same container-based checks locally, use the commands in the [testing guide](TESTING.md). A successful syntax check does not verify SMTP delivery or that the selected thresholds are useful.

## Boundaries

- Monitoring and dashboards are optional and local-only by default.
- Default Alertmanager configuration has no notification integration.
- The email overlay gives Alertmanager outbound network access; it does not attach the API or Prometheus to the `notifications` network.
- There is no remote metrics store, distributed tracing, paging integration, or production identity/firewall policy.
- A remote or multi-user deployment needs a deployment-specific security review before reusing this configuration.
