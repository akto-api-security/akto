package com.akto.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GuardrailLatencyInfo {

    public static String buildGuardrailValidateRequestBody(String aktoAccountId) {
        String time = String.valueOf(System.currentTimeMillis());
        return "{"
                + "\"path\":\"/tools/Add\","
                + "\"requestHeaders\":\"{\\\"host\\\":\\\"arcade.dev\\\",\\\"x-cursor-hook\\\":\\\"beforeSubmitPrompt\\\"}\","
                + "\"method\":\"POST\","
                + "\"requestPayload\":\"{\\\"message\\\":\\\"hi\\\"}\","
                + "\"ip\":\"127.0.0.1\","
                + "\"destIp\":\"127.0.0.1\","
                + "\"time\":\"" + time + "\","
                + "\"statusCode\":\"200\","
                + "\"akto_account_id\":\"" + aktoAccountId + "\","
                + "\"akto_vxlan_id\":\"8a4028fae71a58398112daf2def8b3df\","
                + "\"type\":null,"
                + "\"status\":\"200\","
                + "\"is_pending\":\"false\","
                + "\"source\":\"MIRRORING\","
                + "\"direction\":null,"
                + "\"process_id\":null,"
                + "\"socket_id\":null,"
                + "\"daemonset_id\":null,"
                + "\"enabled_graph\":null,"
                + "\"tag\":\"{\\\"gen-ai\\\": \\\"Gen AI\\\", \\\"ai-agent\\\": \\\"cursor\\\", \\\"source\\\": \\\"ENDPOINT\\\"}\","
                + "\"metadata\":\"{\\\"gen-ai\\\": \\\"Gen AI\\\", \\\"ai-agent\\\": \\\"cursor\\\", \\\"source\\\": \\\"ENDPOINT\\\"}\","
                + "\"contextSource\":\"AGENTIC\""
                + "}";
    }

    public static String buildGuardrailScanRequestBody() {
        return "{"
                + "\"scanner_type\":\"prompt\","
                + "\"scanner_name\":\"PromptInjection\","
                + "\"text\":\"hi\""
                + "}";
    }

    public static final String GUARDRAIL_VALIDATE_REQUEST_PATH = "/api/http-proxy?ingest_data=false&guardrails=true";
    public static final String GUARDRAIL_SCAN_REQUEST_PATH = "/scan";

    public enum GuardrailProbeType {
        VALIDATE(GUARDRAIL_VALIDATE_REQUEST_PATH),
        SCAN(GUARDRAIL_SCAN_REQUEST_PATH);

        private final String requestPath;

        GuardrailProbeType(String requestPath) {
            this.requestPath = requestPath;
        }

        public String getRequestPath() {
            return requestPath;
        }
    }

    public static final List<GuardrailServiceTarget> GUARDRAIL_SERVICE_TARGETS = Arrays.asList(
            new GuardrailServiceTarget(
                    "https://1726615470-guardrails.akto.io",
                    "1726615470",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://ingest.akto.io",
                    "1768175789",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://ingest-demo.akto.io",
                    "xxxxx",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://1737683011-guardrails.akto.io",
                    "1737683011",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://1728622642-guardrails.akto.io",
                    "1728622642",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://data-ingestion.akto.io",
                    "1760499072",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://1767812031-guardrails.akto.io",
                    "1767812031",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://1767812031-proxy-guardrails.akto.io ",
                    "1767812031",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://1767812031-proxy-guardrails-3.akto.io/ ",
                    "1767812031",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://1729478227-guardrails.akto.io",
                    "1729478227",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://1710118493-guardrails.akto.io",
                    "1710118493",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://1745303931-guardrails.akto.io",
                    "1745303931",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://1764882677-guardrails.akto.io ",
                    "1764882677",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://1775456934-guardrails.akto.io",
                    "1775456934",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://1776654502-guardrails.akto.io",
                    "1776654502",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://1703087742-guardrails.akto.io",
                    "1703087742",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://1703087742-guardrails.akto.io",
                    "1703087742",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://1779059321-guardrails.akto.io",
                    "1779059321",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://1779231193-guardrails.akto.io",
                    "1779231193",
                    GuardrailProbeType.VALIDATE),
            new GuardrailServiceTarget(
                    "https://akto-agent-guard-executor.billing-53a.workers.dev",
                    null,
                    GuardrailProbeType.SCAN)
    );

    public static final class GuardrailServiceTarget {
        private final String url;
        private final String accountId;
        private final GuardrailProbeType probeType;

        public GuardrailServiceTarget(String url, String accountId, GuardrailProbeType probeType) {
            this.url = url;
            this.accountId = accountId;
            this.probeType = probeType;
        }

        public String getUrl() {
            return url;
        }

        public String getAccountId() {
            return accountId;
        }

        public GuardrailProbeType getProbeType() {
            return probeType;
        }

        public String getRequestPath() {
            return probeType.getRequestPath();
        }

        public String buildRequestBody() {
            if (probeType == GuardrailProbeType.SCAN) {
                return buildGuardrailScanRequestBody();
            }
            return buildGuardrailValidateRequestBody(accountId);
        }
    }

    public static final class GuardrailLatencyBucketTracker {
        public static final int DEFAULT_BUCKET_SIZE = 5;

        private final int bucketSize;
        private final Map<String, ArrayDeque<Long>> consecutiveSlowByUrl = new ConcurrentHashMap<>();
        private final Map<String, Boolean> slackAlertSentByUrl = new ConcurrentHashMap<>();

        public GuardrailLatencyBucketTracker() {
            this(DEFAULT_BUCKET_SIZE);
        }

        public GuardrailLatencyBucketTracker(int bucketSize) {
            this.bucketSize = Math.max(1, bucketSize);
        }


        public void recordSlow(String url, long latencyMs) {
            ArrayDeque<Long> streak = consecutiveSlowByUrl.computeIfAbsent(url, k -> new ArrayDeque<>(bucketSize));
            synchronized (streak) {
                streak.addLast(latencyMs);
            }
        }


        public void onFastSample(String url) {
            resetBucket(url);
        }

        public boolean hasConsecutiveSlowStreak(String url) {
            ArrayDeque<Long> streak = consecutiveSlowByUrl.get(url);
            if (streak == null) {
                return false;
            }
            synchronized (streak) {
                return streak.size() >= bucketSize;
            }
        }

        public boolean shouldSendSlackAlert(String url) {
            if (!hasConsecutiveSlowStreak(url)) {
                return false;
            }
            if (Boolean.TRUE.equals(slackAlertSentByUrl.get(url))) {
                return false;
            }
            slackAlertSentByUrl.put(url, true);
            return true;
        }

        public List<Long> getRecentLatencies(String url) {
            ArrayDeque<Long> streak = consecutiveSlowByUrl.get(url);
            if (streak == null) {
                return new ArrayList<>();
            }
            synchronized (streak) {
                return new ArrayList<>(streak);
            }
        }

        public int getBucketSize() {
            return bucketSize;
        }

        /** Clears consecutive streak and alert state (after Slack or a fast probe). */
        public void resetAfterSlackAlert(String url) {
            resetBucket(url);
        }

        private void resetBucket(String url) {
            consecutiveSlowByUrl.remove(url);
            slackAlertSentByUrl.remove(url);
        }
    }
}
