# Session Sequence Analyzer

Counts variable-order API sequences per session (Cloudflare API Shield style) and sends the counts
to cyborg (`DataActor.writeSequenceCounts`). Cyborg owns persistence and the model. Gated per account
by `BEHAVIORAL_ANOMALLY_SEQUENCE`; one `SessionAnalyzer` per account, created in `hybrid_runtime/Main`.

## Hot path: `SessionAnalyzer.process`
1. Templatize the URL to an `ApiInfoKey` and drop it if it is not in the catalog.
2. Session id from `impl/SessionIdentifier`: first present of
   cookie `coulomb_sess` > cookie `IDENTITYSID` > header `cp-session-token` >
   `sid` claim of the JWT in `cp-sso-ims-token` (payload only, unverified) > first `x-forwarded-for` IP > source IP.
   Returned as the first 16 hex chars of SHA-256("<source>:<value>"); raw values are never kept or logged.
   Order is overridable with env `BEHAVIOUR_SESSION_ID_SOURCES`
   (`cookie:<name>`, `header:<name>`, `jwt:<header>:<claim>`, `xff`, `ip`, comma separated).
3. `model/SessionState`: ring buffer of the last `maxHistory` (= `nMax`, 2) endpoint ids plus `lastSeen` (event time).
   A gap > `sessionIdleMs` (10 min) clears the history. A consecutive repeat of the same endpoint is ignored.
4. With history h and current endpoint x, for k = 0..min(nMax, |h|) (nMax = 2) increment
   (last k of h -> x) in the hour bucket of the request time. Endpoints are interned to int ids.

## Background (single scheduler thread)
- Every `flushIntervalMs` (5 min): swap the accumulator map (volatile, lock-free) and send every count
  as `SequenceCount{apiCollectionId, context (oldest first, ApiInfoKey.toString()), next, bucketStart, count}`
  through `impl/SequenceCountFlusher` in batches of 1000. No top-N cap. Also flushed by `shutdown()`
  (JVM shutdown hook in `Main`).
- Every `evictionIntervalMs` (1 min): remove sessions idle for more than `sessionIdleMs`, measured against
  the event-time watermark extrapolated by wall time since the last record.

## Tests
`SessionAnalyzerTest` (injectable clock, `triggerFlush` / `triggerEviction` hooks, stubbed DataActor)
and `SessionIdentifierTest`. Run with `RUNTIME_MODE` unset.
