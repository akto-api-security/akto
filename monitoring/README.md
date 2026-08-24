# Cyborg (database-abstractor) monitoring

Local Prometheus scrape job + Grafana dashboard for the cyborg `/metrics` endpoint.

## Prometheus

Add the job from [`prometheus-cyborg.yml`](./prometheus-cyborg.yml) under `scrape_configs:` in your
local `prometheus.yml`, then reload Prometheus.

- Keep the bearer `credentials` in sync with `METRICS_AUTH_TOKEN` in `start-cyborg.sh`
  (default `local-dev-metrics-token`).
- Target host: `host.docker.internal:8085` if Prometheus runs in Docker and cyborg on the host;
  `localhost:8085` if Prometheus runs on the host too.
- The `/metrics` endpoint only serves data when `PROMETHEUS_METRICS_ENABLED=true` (start-cyborg.sh
  sets this). Otherwise the target returns 404.

Verify the target is UP at Prometheus → Status → Targets, or:

```bash
curl -H "Authorization: Bearer local-dev-metrics-token" http://localhost:8085/metrics | head
```

## Grafana

Import [`grafana-cyborg-dashboard.json`](./grafana-cyborg-dashboard.json):
Grafana → Dashboards → New → Import → Upload JSON → pick your Prometheus data source.

Template variables: **Data source**, **Role** (api / consumer / fast-consumer), **Instance**.

Panels: target up, uptime, CPU, heap, request rate, error rate, request rate by status, latency
p50/p90/p99, JVM memory by area, GC pause rate, JVM threads, and Kafka consumer lag (consumer roles).

Metrics carry the common tags `service="cyborg"` and `role` (from `METRICS_SERVICE_ROLE`), which the
dashboard filters on.
