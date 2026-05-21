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

    public static final List<GuardrailServiceTarget> GUARDRAIL_SERVICE_TARGETS = Arrays.asList(
            new GuardrailServiceTarget("https://1726615470-guardrails.akto.io", "1726615470"),
            new GuardrailServiceTarget("https://ingest.akto.io", "1768175789")
    );
    public static final String GUARDRAIL_VALIDATE_REQUEST_PATH = "/api/validate/request";


    public static final class GuardrailServiceTarget {
        private final String url;
        private final String accountId;

        public GuardrailServiceTarget(String url, String accountId) {
            this.url = url;
            this.accountId = accountId;
        }

        public String getUrl() {
            return url;
        }

        public String getAccountId() {
            return accountId;
        }
    }
}
