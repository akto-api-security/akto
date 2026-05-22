package com.akto.util;

import java.util.Arrays;
import java.util.List;

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
}
