# Metrics Pipeline

## Overview

`AllMetrics` ([AllMetrics.java](AllMetrics.java)) is a singleton that owns all metric collection, aggregation, and export for Akto's runtime modules. It follows a **record → aggregate → flush** pattern on a 2-minute export cycle.

## Architecture

```
Application code
    │  calls AllMetrics.instance.setXxx(val)
    ▼
Metric objects (in-memory accumulators)
    │  ScheduledExecutorService fires every 120s
    ▼
getMetricAndReset() per metric → List<MetricData>
    │
    ▼
DataActor.ingestMetricData()
    ├── DbActor  → MongoDB collection: metrics_data (capped, 100k docs / 100 MB)
    └── ClientActor → HTTP POST to dashboard /ingestMetricsData
```

## Metric Types

All metrics extend the abstract inner class `Metric` and implement `record(float)`, `getMetric()`, and `getMetricAndReset()`.

| Type | Class | Semantics |
|------|-------|-----------|
| `LATENCY` | `LatencyMetric` | Running `total/count` average; resets both on flush |
| `SUM` | `SumMetric` | Accumulates additions; resets to 0 on flush |
| `MAX` | `MaxMetric` | Tracks highest value seen; resets to 0 on flush |
| `GAUGE` | `GaugeMetric` | Last-write-wins; resets to 0 on flush |

## Initialization

Call `AllMetrics.instance.init(...)` once at module startup. Which metrics are created depends on the module:

```java
AllMetrics.instance.init(
    LogDb.RUNTIME,          // module type — controls which metric groups are created
    checkPg,                // true → PostgreSQL metrics group is created
    dataActor,              // DbActor or ClientActor
    accountId,
    instanceId,             // used as instance_id on every MetricData record
    ModuleInfo.ModuleType.MINI_RUNTIME.name()
);
```

Metrics are `null` until `init()` is called. All setters guard against null before recording.

## Kafka Consumer Metrics (Mini-Runtime)

Collected in [Main.java](../../../../../../../../apps/mini-runtime/src/main/java/com/akto/hybrid_runtime/Main.java) via a `scheduleAtFixedRate` task every **1 minute**.

```java
Map<MetricName, ? extends Metric> metrics = main.consumer.metrics();
// Skips per-partition (tag "partition") and per-topic (tag "topic") metrics
// Only processes consumer-level aggregated metrics
```

| Kafka metric name | AllMetrics field | Type | Metric ID |
|---|---|---|---|
| `records-lag-max` | `kafkaRecordsLagMax` | `MaxMetric` | `KAFKA_RECORDS_LAG_MAX` |
| `records-consumed-rate` | `kafkaRecordsConsumedRate` | `GaugeMetric` | `KAFKA_RECORDS_CONSUMED_RATE` |
| `fetch-latency-avg` | `kafkaFetchAvgLatency` | `GaugeMetric` | `KAFKA_FETCH_AVG_LATENCY` |
| `bytes-consumed-rate` | `kafkaBytesConsumedRate` | `GaugeMetric` | `KAFKA_BYTES_CONSUMED_RATE` |

`Double.NaN` values from the Kafka API are normalized to `0.0` before recording.

## All Metric Groups

### Runtime (LogDb.RUNTIME)

| Metric ID | Type | Description |
|---|---|---|
| `RT_KAFKA_RECORD_COUNT` | SUM | Kafka records processed by runtime |
| `RT_KAFKA_RECORD_SIZE` | SUM | Total bytes of Kafka records processed |
| `RT_KAFKA_LATENCY` | LATENCY | Average processing latency per record |
| `RT_API_RECEIVED_COUNT` | SUM | APIs received from traffic |
| `KAFKA_RECORDS_LAG_MAX` | MAX | Max consumer lag across partitions |
| `KAFKA_RECORDS_CONSUMED_RATE` | GAUGE | Records/sec consumed |
| `KAFKA_FETCH_AVG_LATENCY` | GAUGE | Average fetch latency (ms) |
| `KAFKA_BYTES_CONSUMED_RATE` | GAUGE | Bytes/sec consumed |
| `CYBORG_NEW_API_COUNT` | SUM | New APIs discovered via Cyborg |
| `CYBORG_TOTAL_API_COUNT` | SUM | Total APIs processed via Cyborg |
| `CYBORG_API_PAYLOAD_SIZE` | SUM | Payload bytes sent to Cyborg |
| `DELTA_CATALOG_TOTAL_COUNT` | SUM | Total catalog entries processed |
| `DELTA_CATALOG_NEW_COUNT` | SUM | New catalog entries discovered |

### PostgreSQL (when `pgMetrics=true`)

| Metric ID | Type | Description |
|---|---|---|
| `PG_SAMPLE_DATA_INSERT_COUNT` | SUM | Rows inserted into sample data |
| `PG_SAMPLE_DATA_INSERT_LATENCY` | LATENCY | Insert latency |
| `MERGING_JOB_LATENCY` | LATENCY | URL merging job latency |
| `MERGING_JOB_URLS_UPDATED_COUNT` | SUM | URLs updated during merging |
| `MERGING_JOB_URL_UPDATE_LATENCY` | LATENCY | Per-URL update latency |
| `STALE_SAMPLE_DATA_CLEANUP_JOB_LATENCY` | LATENCY | Cleanup job latency |
| `STALE_SAMPLE_DATA_DELETED_COUNT` | SUM | Stale rows deleted |
| `TOTAL_SAMPLE_DATA_COUNT` | SUM | Total sample data rows |
| `PG_DATA_SIZE_IN_MB` | SUM | PostgreSQL database size |

### Testing (LogDb.TESTING)

| Metric ID | Type | Description |
|---|---|---|
| `TESTING_RUN_COUNT` | SUM | Security test runs executed |
| `TESTING_RUN_LATENCY` | LATENCY | Test run latency |
| `SAMPLE_DATA_FETCH_LATENCY` | LATENCY | Single sample fetch latency |
| `MULTIPLE_SAMPLE_DATA_FETCH_LATENCY` | LATENCY | Bulk sample fetch latency |

### Cyborg (always initialized)

| Metric ID | Type | Description |
|---|---|---|
| `CYBORG_CALL_LATENCY` | LATENCY | Latency of calls to Cyborg service |
| `CYBORG_CALL_COUNT` | SUM | Number of Cyborg calls |
| `CYBORG_DATA_SIZE` | SUM | Data size sent to Cyborg |

### Infrastructure (always initialized, collected via JMX MXBeans)

| Metric ID | Type | Source |
|---|---|---|
| `CPU_USAGE_PERCENT` | GAUGE | `OperatingSystemMXBean.getProcessCpuLoad() * 100` |
| `HEAP_MEMORY_USED_MB` | GAUGE | `MemoryMXBean.getHeapMemoryUsage().getUsed()` |
| `HEAP_MEMORY_MAX_MB` | GAUGE | `MemoryMXBean.getHeapMemoryUsage().getMax()` |
| `NON_HEAP_MEMORY_USED_MB` | GAUGE | `MemoryMXBean.getNonHeapMemoryUsage().getUsed()` |
| `THREAD_COUNT` | GAUGE | `ThreadMXBean.getThreadCount()` |
| `AVAILABLE_PROCESSORS` | GAUGE | `Runtime.getRuntime().availableProcessors()` |
| `TOTAL_PHYSICAL_MEMORY_MB` | GAUGE | `OperatingSystemMXBean.getTotalPhysicalMemorySize()` |

### Traffic Collector (per-instance, dynamic)

Stored in `ConcurrentHashMap<String, Metric>` keyed by instance ID. Dead instances (value=0 after flush) are automatically pruned from the map.

| Metric ID | Type | Description |
|---|---|---|
| `TC_CPU_USAGE` | GAUGE | CPU usage per TC instance |
| `TC_MEMORY_USAGE` | GAUGE | Memory usage per TC instance |

## Export Schedule

| Task | Interval | What happens |
|---|---|---|
| Kafka consumer metric polling | 1 minute | Reads `KafkaConsumer.metrics()`, calls `setKafkaXxx()` |
| AllMetrics flush | 2 minutes (`scheduleWithFixedDelay`) | `collectInfraMetrics()` + `getMetricAndReset()` all metrics → `DataActor.ingestMetricData()` |

## Adding a New Metric

1. Declare a `private Metric myNewMetric = null;` field in `AllMetrics`.
2. In `init()`, instantiate it with the appropriate type: `myNewMetric = new SumMetric("MY_METRIC_ID", 60, accountId, orgId, moduleType);`
3. Add it to the `metrics` list (line ~86-94) so the flush loop includes it.
4. Add a public setter: `public void setMyNewMetric(float val) { if (myNewMetric != null) myNewMetric.record(val); }`
5. Call the setter from application code.

## MetricData DTO

Each flushed metric becomes a `MetricData` object (in `libs/dao`) with:
- `metricId` — string ID (e.g., `"KAFKA_RECORDS_LAG_MAX"`)
- `value` — aggregated float
- `orgId` — organization identifier
- `instanceId` — module instance name
- `moduleType` — `MINI_RUNTIME`, `MINI_TESTING`, or `TRAFFIC_COLLECTOR`
- `metricType` — `SUM`, `LATENCY`, `MAX`, or `GAUGE`
- `moduleInfo` — snapshot of module health (without `additionalData` to reduce size)
- `timestamp` — auto-set at construction time
