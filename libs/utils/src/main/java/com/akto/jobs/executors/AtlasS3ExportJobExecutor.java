package com.akto.jobs.executors;

import com.akto.billing.UsageMetricUtils;
import com.akto.dao.AtlasS3ExportIntegrationDao;
import com.akto.dto.AtlasS3ExportIntegration;
import com.akto.dto.jobs.AtlasS3ExportJobParams;
import com.akto.dto.jobs.Job;
import com.akto.jobs.JobExecutor;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.DashboardMode;
import com.akto.utils.elasticsearch.ElasticSearchClient;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.Filters;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * ATLAS_S3_EXPORT recurring job: reads the account's Atlas or Argus traces from Elasticsearch since the last
 * checkpoint and writes them to the customer's S3 bucket as gzipped OTLP/JSON
 * (ExportTraceServiceRequest), one object per page. Keys follow the OpenTelemetry Collector
 * awss3exporter layout and are deterministic per (window, page), so a retried window overwrites
 * its own objects instead of duplicating them. The checkpoint only moves past a window once the
 * whole window is uploaded.
 */
public class AtlasS3ExportJobExecutor extends JobExecutor<AtlasS3ExportJobParams> {

    private static final LoggerMaker logger = new LoggerMaker(AtlasS3ExportJobExecutor.class, LogDb.DASHBOARD);
    public static final AtlasS3ExportJobExecutor INSTANCE = new AtlasS3ExportJobExecutor();

    // Traces per ES read and per file; kept small because prompts/responses are stored untruncated.
    private static final int PAGE_SIZE = 100;
    private static final long WINDOW_MS = 60 * 60 * 1000L;
    // Old history is sent a slice per run (the job recurs every 5 min) so a backfill never loads ES
    // or the jobs lane all at once. A started window is always finished, so every run advances at
    // least 1h of trace time and a backlog always shrinks; new traffic can never fall behind.
    private static final int MAX_SPANS_PER_RUN = 20_000;
    // Small pause between page uploads so even one large window is sent gradually, not in a burst.
    private static final long PAGE_PAUSE_MS = 200;
    // Traces can be indexed after their own timestamp (mini-runtime batching, cyborg Kafka lag);
    // stay this far behind now so a window is complete when it is exported. A trace indexed more
    // than this late lands behind the checkpoint and is not exported.
    private static final long INGESTION_LAG_MS = 15 * 60 * 1000L;
    private static final String SCROLL_KEEP_ALIVE = "2m";
    private static final int SPAN_KIND_CLIENT = 3;
    private static final Pattern HEX = Pattern.compile("^[0-9a-f]+$");
    private static final DateTimeFormatter KEY_TIME = DateTimeFormatter
        .ofPattern("'year='yyyy/'month='MM/'day='dd/'hour='HH/'minute='mm").withZone(ZoneOffset.UTC);

    private AtlasS3ExportJobExecutor() {
        super(AtlasS3ExportJobParams.class);
    }

    @Override
    protected void runJob(Job job) throws Exception {
        AtlasS3ExportJobParams params = paramClass.cast(job.getJobParams());
        // SaaS-only and behind a feature flag; when off, do nothing and keep the checkpoint.
        if (DashboardMode.isOnPremDeployment()
            || !UsageMetricUtils.getFeatureAccessSaas(job.getAccountId(), AtlasS3ExportIntegration.FEATURE_LABEL).getIsGranted()) {
            logger.info("Atlas S3 export not enabled for account " + job.getAccountId() + ", skipping");
            return;
        }
        AtlasS3ExportIntegration config = AtlasS3ExportIntegrationDao.instance.findOne(
            Filters.eq(AtlasS3ExportIntegration.SOURCE, params.getSource()));
        if (config == null || StringUtils.isBlank(config.getBucketName()) || StringUtils.isBlank(config.getRegion())) {
            logger.info("Atlas S3 export not configured for account " + job.getAccountId() + ", skipping");
            return;
        }
        boolean credentialsMissing = config.usesAccessKeys()
            ? StringUtils.isBlank(config.getAccessKeyId()) || StringUtils.isBlank(config.getSecretAccessKey())
            : StringUtils.isBlank(config.getRoleArn());
        if (credentialsMissing) {
            throw new Exception("AWS credentials are not set for the Atlas S3 export");
        }
        ElasticSearchClient es = ElasticSearchClient.instance();
        if (!es.isConfigured()) {
            throw new Exception("Elasticsearch is not configured, cannot read traces");
        }

        boolean atlas = CONTEXT_SOURCE.ENDPOINT.name().equals(params.getSource());
        long end = System.currentTimeMillis() - INGESTION_LAG_MS;
        // 0 = never exported: start from the account's oldest trace so all history is sent.
        long from = params.getLastExportedTs() > 0 ? params.getLastExportedTs()
            : earliestTraceTs(es, job.getAccountId(), atlas, end);
        int exportedThisRun = 0;
        while (from < end && exportedThisRun < MAX_SPANS_PER_RUN) {
            long to = Math.min(from + WINDOW_MS, end);
            int spans = exportWindow(es, job, config, atlas, from, to);
            exportedThisRun += spans;
            if (spans > 0) {
                logger.info("Atlas S3 export: account " + job.getAccountId() + " (" + params.getSource() + ") exported " + spans
                    + " spans for [" + from + ", " + to + ")");
            }
            params.setLastExportedTs(to);
            updateJobParams(job, params);
            updateJobHeartbeat(job);
            from = to;
        }
    }

    /** Timestamp of the account's oldest matching trace, or {@code end} when there is none yet. */
    private long earliestTraceTs(ElasticSearchClient es, int accountId, boolean atlas, long end) throws Exception {
        JSONObject query = tracesQuery(es, accountId, 0, end, atlas);
        JSONObject aggs = new JSONObject().put("earliest", new JSONObject()
            .put("min", new JSONObject().put("field", "timestamp")));
        JSONObject result = es.aggregate(query, aggs);
        if (result == null) {
            throw new Exception("Elasticsearch read failed while finding the oldest trace");
        }
        JSONObject earliest = result.optJSONObject("earliest");
        if (earliest == null || earliest.isNull("value")) return end;
        return Math.min((long) earliest.optDouble("value", end), end);
    }

    // Pages are read in timestamp order and uploaded one after another, so files are written oldest first.
    private int exportWindow(ElasticSearchClient es, Job job, AtlasS3ExportIntegration config,
                             boolean atlas, long from, long to) throws Exception {
        int accountId = job.getAccountId();
        String host = System.getenv("ES_HOST").replaceAll("/+$", "");
        JSONObject query = tracesQuery(es, accountId, from, to, atlas);
        JSONObject body = new JSONObject()
            .put("query", query)
            .put("size", PAGE_SIZE)
            .put("sort", new JSONArray().put(new JSONObject().put("timestamp", new JSONObject().put("order", "asc"))));

        // httpPost returns null on any failure, so a failed read is never mistaken for "no traces".
        JSONObject response = es.httpPost(host + "/" + es.getIndex() + "/_search?scroll=" + SCROLL_KEEP_ALIVE, body.toString());
        int page = 0;
        int spans = 0;
        while (true) {
            if (response == null) {
                throw new Exception("Elasticsearch read failed for window [" + from + ", " + to + ")");
            }
            JSONObject outer = response.optJSONObject("hits");
            JSONArray hits = outer != null ? outer.optJSONArray("hits") : null;
            if (hits == null || hits.length() == 0) break;

            JSONArray sources = new JSONArray();
            for (int i = 0; i < hits.length(); i++) {
                JSONObject source = hits.getJSONObject(i).optJSONObject("_source");
                if (source != null) sources.put(source);
            }
            byte[] gz = gzip(toOtlpJson(accountId, sources));
            S3Uploader.put(accountId, config, buildKey(config.getPrefix(), from, page), gz);
            spans += sources.length();
            page++;
            // Heartbeat per page: a slow window must not look stale (300s) and get picked up by a
            // second runner, which would export the same range in parallel and out of order.
            updateJobHeartbeat(job);

            String scrollId = response.optString("_scroll_id", "");
            if (hits.length() < PAGE_SIZE || scrollId.isEmpty()) break;
            Thread.sleep(PAGE_PAUSE_MS);
            response = es.httpPost(host + "/_search/scroll",
                new JSONObject().put("scroll", SCROLL_KEEP_ALIVE).put("scroll_id", scrollId).toString());
        }
        return spans;
    }

    // Same split as the Traces pages: Atlas jobs export isAtlasTraffic=true, Argus jobs everything
    // else, including older traces stored before the field existed.
    private static JSONObject tracesQuery(ElasticSearchClient es, int accountId, long from, long to, boolean atlas)
            throws JSONException {
        JSONObject query = es.buildBaseQueryMulti(accountId, from, to, null, null);
        JSONObject atlasTerm = new JSONObject().put("term", new JSONObject().put("isAtlasTraffic", true));
        JSONObject bool = query.getJSONObject("bool");
        if (atlas) bool.getJSONArray("must").put(atlasTerm);
        else bool.put("must_not", new JSONArray().put(atlasTerm));
        return query;
    }

    static String buildKey(String prefix, long windowStartMs, int page) {
        String path = KEY_TIME.format(Instant.ofEpochMilli(windowStartMs))
            + "/traces_" + windowStartMs + "_" + String.format("%05d", page) + ".json.gz";
        return StringUtils.isBlank(prefix) ? path : prefix + "/" + path;
    }

    /** Maps Elasticsearch agent-query documents (_source) to an OTLP/JSON ExportTraceServiceRequest. */
    static JSONObject toOtlpJson(int accountId, JSONArray sources) throws JSONException {
        // One resource per application (claudecli, cursor, ...) so it maps to OTel service.name.
        Map<String, JSONArray> spansByService = new LinkedHashMap<>();
        for (int i = 0; i < sources.length(); i++) {
            JSONObject doc = sources.getJSONObject(i);
            String service = str(doc, "serviceId");
            spansByService.computeIfAbsent(service.isEmpty() ? "unknown" : service, k -> new JSONArray()).put(toSpan(doc));
        }
        JSONArray resourceSpans = new JSONArray();
        for (Map.Entry<String, JSONArray> e : spansByService.entrySet()) {
            JSONArray resourceAttrs = new JSONArray()
                .put(attr("service.name", e.getKey()))
                .put(intAttr("akto.account_id", accountId));
            JSONObject scopeSpans = new JSONObject()
                .put("scope", new JSONObject().put("name", "akto-atlas"))
                .put("spans", e.getValue());
            resourceSpans.put(new JSONObject()
                .put("resource", new JSONObject().put("attributes", resourceAttrs))
                .put("scopeSpans", new JSONArray().put(scopeSpans)));
        }
        return new JSONObject().put("resourceSpans", resourceSpans);
    }

    private static JSONObject toSpan(JSONObject doc) throws JSONException {
        String queryPayload = str(doc, "queryPayload");
        String responsePayload = str(doc, "responsePayload");
        String model = extractModel(queryPayload);
        if (model.isEmpty()) model = extractModel(responsePayload);

        JSONArray attrs = new JSONArray();
        addAttr(attrs, "gen_ai.request.model", model);
        attrs.put(intAttr("gen_ai.usage.input_tokens", doc.optLong("inputTokens", 0)));
        attrs.put(intAttr("gen_ai.usage.output_tokens", doc.optLong("outputTokens", 0)));
        addAttr(attrs, "user.name", str(doc, "userName"));
        addAttr(attrs, "session.id", str(doc, "sessionIdentifier"));
        addAttr(attrs, "host.id", str(doc, "deviceId"));
        addAttr(attrs, "akto.trace_id", str(doc, "traceId"));
        addAttr(attrs, "akto.span_id", str(doc, "spanId"));
        // Only present when guardrails actually ran on this traffic.
        if (doc.has("guardrailViolated") && !doc.isNull("guardrailViolated")) {
            attrs.put(new JSONObject().put("key", "akto.guardrail.violated")
                .put("value", new JSONObject().put("boolValue", doc.optBoolean("guardrailViolated", false))));
            addAttr(attrs, "akto.guardrail.action", str(doc, "guardrailAction"));
            addAttr(attrs, "akto.guardrail.policy", str(doc, "guardrailPolicy"));
            addAttr(attrs, "akto.guardrail.rule", str(doc, "guardrailRule"));
            addAttr(attrs, "akto.guardrail.reason", str(doc, "guardrailReason"));
            addAttr(attrs, "akto.guardrail.severity", str(doc, "guardrailSeverity"));
        }
        addAttr(attrs, "akto.request.payload", queryPayload);
        addAttr(attrs, "akto.response.payload", responsePayload);

        String timeNs = String.valueOf(doc.optLong("timestamp", 0) * 1_000_000L);
        String traceSource = !str(doc, "traceId").isEmpty() ? str(doc, "traceId") : str(doc, "sessionIdentifier");
        return new JSONObject()
            .put("traceId", toHexId(traceSource, 32))
            .put("spanId", toHexId(str(doc, "spanId"), 16))
            .put("name", model.isEmpty() ? "chat" : "chat " + model)
            .put("kind", SPAN_KIND_CLIENT)
            .put("startTimeUnixNano", timeNs)
            .put("endTimeUnixNano", timeNs)
            .put("attributes", attrs);
    }

    /** OTLP needs hex ids (32 chars trace, 16 chars span); non-hex ids are hashed deterministically. */
    static String toHexId(String id, int length) {
        if (StringUtils.isBlank(id)) id = UUID.randomUUID().toString();
        String lower = id.trim().toLowerCase();
        if (lower.length() == length && HEX.matcher(lower).matches() && !lower.matches("^0+$")) return lower;
        try {
            byte[] md5 = MessageDigest.getInstance("MD5").digest(id.getBytes(StandardCharsets.UTF_8));
            return String.format("%032x", new BigInteger(1, md5)).substring(0, length);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // MD5 is always available in the JDK
        }
    }

    private static String extractModel(String payload) {
        if (StringUtils.isBlank(payload)) return "";
        try {
            return new JSONObject(payload).optString("model", "");
        } catch (Exception e) {
            return "";
        }
    }

    private static String str(JSONObject doc, String key) {
        return doc.isNull(key) ? "" : doc.optString(key, "");
    }

    private static void addAttr(JSONArray attrs, String key, String value) throws JSONException {
        if (StringUtils.isNotBlank(value)) attrs.put(attr(key, value));
    }

    private static JSONObject attr(String key, String value) throws JSONException {
        return new JSONObject().put("key", key).put("value", new JSONObject().put("stringValue", value));
    }

    // OTLP/JSON encodes int64 as a string.
    private static JSONObject intAttr(String key, long value) throws JSONException {
        return new JSONObject().put("key", key).put("value", new JSONObject().put("intValue", String.valueOf(value)));
    }

    // Streams the JSON straight into gzip, so the uncompressed file is never held in memory.
    private static byte[] gzip(JSONObject json) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(out), StandardCharsets.UTF_8)) {
            json.write(writer);
        }
        return out.toByteArray();
    }

    /**
     * AWS calls kept in their own class so the SDK is only loaded when an export actually runs.
     * Two auth modes, chosen per account in the connector:
     * ROLE - assumes the customer's IAM role (which trusts Akto's AWS account) with ExternalId = Akto
     *   account id. Akto runs outside AWS, so the caller identity is an Akto IAM user whose keys are
     *   given only to this job via AKTO_ATLAS_EXPORT_AWS_ACCESS_KEY_ID / _SECRET_ACCESS_KEY; dedicated
     *   names so no other AWS code in the process picks them up.
     * ACCESS_KEYS - uses the customer's own IAM user keys directly; no Akto AWS account involved.
     */
    private static final class S3Uploader {
        private static final String ACCESS_KEY_ENV = "AKTO_ATLAS_EXPORT_AWS_ACCESS_KEY_ID";
        private static final String SECRET_KEY_ENV = "AKTO_ATLAS_EXPORT_AWS_SECRET_ACCESS_KEY";
        private static final Map<Integer, String> SIGNATURES = new ConcurrentHashMap<>();
        private static final Map<Integer, S3Client> CLIENTS = new ConcurrentHashMap<>();

        static void put(int accountId, AtlasS3ExportIntegration config, String key, byte[] gzippedJson) {
            client(accountId, config).putObject(PutObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(key)
                    .contentType("application/json")
                    .contentEncoding("gzip")
                    .build(),
                RequestBody.fromBytes(gzippedJson));
        }

        // Rebuilt only when region/role changes; the replaced client is left to GC since a run
        // for the same account may still hold it.
        private static synchronized S3Client client(int accountId, AtlasS3ExportIntegration config) {
            String signature = config.usesAccessKeys()
                ? config.getRegion() + "|keys|" + config.getAccessKeyId() + "|" + config.getSecretAccessKey().hashCode()
                : config.getRegion() + "|role|" + config.getRoleArn();
            S3Client cached = CLIENTS.get(accountId);
            if (cached != null && signature.equals(SIGNATURES.get(accountId))) return cached;

            S3Client client = config.usesAccessKeys() ? accessKeyClient(config) : roleClient(accountId, config);
            CLIENTS.put(accountId, client);
            SIGNATURES.put(accountId, signature);
            return client;
        }

        private static S3Client accessKeyClient(AtlasS3ExportIntegration config) {
            return S3Client.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey())))
                .build();
        }

        private static S3Client roleClient(int accountId, AtlasS3ExportIntegration config) {
            String accessKey = System.getenv(ACCESS_KEY_ENV);
            String secretKey = System.getenv(SECRET_KEY_ENV);
            if (StringUtils.isBlank(accessKey) || StringUtils.isBlank(secretKey)) {
                throw new IllegalStateException(ACCESS_KEY_ENV + " / " + SECRET_KEY_ENV + " are not set on this deployment");
            }
            // STS in us-east-1: its session tokens work in every region, including opt-in ones.
            StsClient sts = StsClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
            return S3Client.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StsAssumeRoleCredentialsProvider.builder()
                    .stsClient(sts)
                    .refreshRequest(AssumeRoleRequest.builder()
                        .roleArn(config.getRoleArn())
                        .roleSessionName("akto-atlas-export-" + accountId)
                        .externalId(String.valueOf(accountId))
                        .build())
                    .build())
                .build();
        }
    }
}
