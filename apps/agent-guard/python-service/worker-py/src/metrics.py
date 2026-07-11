"""Business metrics. HTTP-level metrics (request count/latency/status) come
from prometheus-fastapi-instrumentator instead, wired in app.py."""

from prometheus_client import Counter, Gauge, Histogram

SCANS_TOTAL = Counter(
    "scanner_requests_total",
    "Number of scan requests",
    ["scanner", "status"],
)

SCAN_DURATION = Histogram(
    "scanner_duration_seconds",
    "Scan handler duration",
    ["scanner"],
)

PROVIDER_LATENCY = Histogram(
    "provider_latency_seconds",
    "Latency of LLM provider calls",
    ["provider"],
)

PROVIDER_ERRORS = Counter(
    "provider_errors_total",
    "LLM provider call failures",
    ["provider", "reason"],
)

BACKPRESSURE_TRIPS = Counter(
    "cascade_backpressure_trips_total",
    "Times a scan was skipped due to cascade backpressure",
)

QUEUE_SIZE = Gauge(
    "scan_queue_size",
    "Current number of in-flight /scan requests",
)

ANONYMIZER_LATENCY = Histogram(
    "anonymizer_latency_seconds",
    "Latency of calls to the anonymizer service",
)

ANONYMIZER_ERRORS = Counter(
    "anonymizer_errors_total",
    "Anonymizer call failures",
    ["reason"],
)
