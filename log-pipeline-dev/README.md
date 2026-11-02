# Log pipeline dev setup (Kafka -> consumer -> NEW logs MongoDB 8)

Everything log-related lives in this compose. **Cyborg runs on your host** (`../start-cyborg.sh`) and keeps using
your existing local mongod as its main DB.

```
cyborg (host, :9095) --LOG_KAFKA_ENABLED=true--> kafka (:9192) --> log-consumer(s) --> mongo-logs (:27018, MongoDB 8.0)
```

## Start / stop
```bash
cd log-pipeline-dev
docker compose up -d                      # kafka, kafka-init (topics), mongo-logs, log-consumer
docker compose up -d --scale log-consumer=3   # more consumers (same group; max = 10 partitions)
docker compose down                       # stop (add -v to wipe data)
```
First start builds the project inside the consumer container (a few minutes). Later restarts: `SKIP_BUILD=true docker compose up -d --force-recreate log-consumer`.

Host ports: Kafka 9192, mongo-logs 27018 (override with `KAFKA_HOST_PORT`, `MONGO_LOGS_PORT`; the defaults avoid a local
mongod on 27017 and another kafka on 9092).

## Run cyborg with the pipeline ON
```bash
cd ..    # repo root
AKTO_MONGO_CONN=mongodb://localhost:27017 LOG_KAFKA_ENABLED=true LOG_KAFKA_BROKER_URL=localhost:9192 ./start-cyborg.sh
# or: cp log-pipeline-dev/cyborg.env.example local.env && ./start-cyborg.sh
```
`LOG_KAFKA_ENABLED` is the only switch. Unset/false = today's behaviour (direct Mongo writes), no Kafka client is created.
Moved endpoints: insertRuntimeLog, insertTestingLog, insertProtectionLog, insertEndpointShieldLog, bulkWriteTrafficMetrics.

## Stop / resume the consumer (Kafka keeps buffering)
```bash
docker compose exec log-consumer touch /tmp/log-consumer.pause   # pause (all threads in that container)
docker compose exec log-consumer rm /tmp/log-consumer.pause      # resume
# or start with LOG_CONSUMER_PAUSED=true
```
Dedicated consumers per topic: `LOG_CONSUMER_ROLES=logs` or `metrics`. All consumer settings are in `consumer.env`.

## Verify
```bash
# lag per group
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:19092 --describe --group log-writer-logs
# rows in the NEW logs DB (database name = accountId)
docker compose exec mongo-logs mongosh --quiet --eval 'const d=db.getSiblingDB("<accountId>"); d.getCollectionNames().forEach(c=>print(c,d[c].countDocuments()))'
# tail messages
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:19092 --topic akto.logs --from-beginning --max-messages 5
```
Local API calls need a JWT checked against `common.configs` HYBRID_SAAS in cyborg's main Mongo:
`./mint-dev-token.sh <accountId>` seeds a key and prints a token (it targets the optional `mongo-main` container:
`docker compose --profile main-db up -d mongo-main`, then run cyborg with `AKTO_MONGO_CONN=mongodb://localhost:27117`).
Send it as the raw `authorization` header. Body example: `{"log":{"log":"hi","key":"INFO","timestamp":1700000000}}` to `/api/insertRuntimeLog`.

## Verified end to end (flag on / off / Kafka down)
- on: rows only in mongo-logs; off: rows only in main DB; Kafka stopped: API stays 200 at ~5 ms.
