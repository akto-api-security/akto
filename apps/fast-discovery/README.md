# Fast-Discovery Consumer

A lightweight parallel Kafka consumer for real-time new API discovery with <1 second latency.

## Purpose

Fast-discovery runs independently alongside mini-runtime as a separate consumer on the same Kafka topic (`akto.api.logs`). It focuses exclusively on detecting **new APIs** quickly and making them visible in the dashboard, while mini-runtime handles full payload processing and enrichment.

## Architecture

```
Kafka Topic: akto.api.logs
    ├─> Mini-Runtime (Consumer Group: "mini-runtime")
    │   └─> Full processing: parameters, samples, types
    │
    └─> Fast-Discovery (Consumer Group: "fast-discovery")
        ├─> Parse message → Extract URL, method, apiCollectionId
        ├─> Bloom filter check (99% filter rate)
        ├─> Batch database verification
        └─> HTTP POST to Database-Abstractor
                ↓
        Database-Abstractor (Port 9000)
        ├─> HTTP Endpoints: /api/bulkWriteSti, /api/bulkWriteApiInfo
        └─> MongoDB (single_type_info, api_info, api_collections)
```

## Performance Characteristics

- **New API Latency:** <1 second (vs 1-5 seconds with full processing)
- **Throughput:** 12,500 APIs/sec per instance
- **Memory:** 170MB (120MB Bloom filter + 50MB overhead)
- **Startup Time:** 10-30 seconds (load existing APIs into Bloom filter)

## What Fast-Discovery Does

1. **Parse Kafka message:** Extract URL, method, apiCollectionId (~150 lines)
2. **Bloom filter check:** O(1) duplicate detection (99% filter rate)
3. **HTTP batch check:** Verify non-existence via database-abstractor API (1000 APIs per batch)
4. **HTTP POST to database-abstractor:** Send new APIs for insertion

**Note:** ALL database operations go through database-abstractor HTTP API - NO direct MongoDB access!

## What Fast-Discovery Skips (Handled by Mini-Runtime)

- ❌ JSON flattening (~50ms per API)
- ❌ Parameter extraction (~100ms per API)
- ❌ Type inference (~50ms per parameter)
- ❌ URL template detection (~200ms per pattern)
- ❌ Sample data collection (~20ms per sample)
- ❌ Sensitive data detection (~30ms per parameter)

**Time saved:** 1-5 seconds per API → Enables <1s latency

## Configuration

Environment variables:

```bash
# Kafka Consumer
AKTO_KAFKA_BROKER_URL=kafka1:19092
AKTO_KAFKA_TOPIC_NAME=akto.api.logs
AKTO_KAFKA_GROUP_ID_CONFIG=fast-discovery
AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG=1000

# Database-Abstractor HTTP Client (ALL database operations)
DB_ABSTRACTOR_URL=http://database-abstractor:9000
DB_ABSTRACTOR_JWT_TOKEN=<jwt-token>

# Bloom Filter
BLOOM_FILTER_EXPECTED_SIZE=10000000
BLOOM_FILTER_FPP=0.01
```

## Building

```bash
# Build with Maven
mvn clean package

# Run
java -jar target/fast-discovery-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## Components

- **Main.java:** Entry point, Kafka consumer setup
- **FastDiscoveryConsumer.java:** Three-stage detection pipeline (uses SampleParser for message parsing)
- **BloomFilterManager.java:** Bloom filter initialization and management
- **ApiCollectionResolver.java:** Collection ID resolution by hostname
- **DatabaseAbstractorClient.java:** Wrapper for DataActor (ClientActor/DbActor) for database operations
- **BulkUpdatesBuilder.java:** Convert to BulkUpdates format
- **BulkUpdates.java:** DTO for database-abstractor API

## Dashboard Integration

**Immediate View (<1 second):**
- API appears in inventory listing
- Basic info visible: URL, method, collection, timestamp
- Status: "Pending analysis..."

**After Mini-Runtime Processing (~10 seconds):**
- Full parameter details visible
- Sample requests/responses displayed
- Sensitive data detected and flagged
- Status: "Fully analyzed"

## Monitoring

Key metrics:
- Bloom filter hit rate (target: >99%)
- Batch processing time (target: <100ms per 1000 messages)
- API discovery latency (target: <1s)
- Kafka consumer lag
