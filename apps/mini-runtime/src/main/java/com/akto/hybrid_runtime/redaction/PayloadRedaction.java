package com.akto.hybrid_runtime.redaction;

import com.akto.dto.HttpResponseParams;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import java.util.function.Consumer;

/** Entry point for redacting stored payloads; off unless AKTO_GUARDRAIL_REDACTION_ENABLED=true and _URL is set. */
public final class PayloadRedaction {

    private static final Object LOCK = new Object();
    private static volatile boolean initialised;
    private static RedactionConfig config;
    private static GuardrailRedactionClient client;
    private static AgentRecordRedactionQueue agentQueue;

    private PayloadRedaction() {
    }

    public static void storeAgentRecord(AgentQueryRecord record, HttpResponseParams params, Consumer<AgentQueryRecord> sink) {
        init();
        if (agentQueue == null) {
            sink.accept(record);
            return;
        }
        String method = params.getRequestParams().getMethod();
        String path = params.getRequestParams().getURL();
        String host = HttpCallParser.getHeaderValue(params.getRequestParams().getHeaders(), "host");
        agentQueue.submit(record, method, host == null ? "" : host, path, sink);
    }

    public static OrigRedactor newOrigRedactor() {
        init();
        return client == null ? null : new OrigRedactor(client, config.budgetMs, config.skipWhenUnavailable);
    }

    private static void init() {
        if (initialised) {
            return;
        }
        synchronized (LOCK) {
            if (initialised) {
                return;
            }
            config = RedactionConfig.fromEnv();
            client = GuardrailRedactionClient.fromEnv(config);
            agentQueue = client == null ? null : new AgentRecordRedactionQueue(client, config);
            initialised = true;
        }
    }
}
