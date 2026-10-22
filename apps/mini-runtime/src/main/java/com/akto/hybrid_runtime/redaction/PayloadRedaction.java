package com.akto.hybrid_runtime.redaction;

import com.akto.dto.HttpResponseParams;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.utils.elasticsearch.AgentQueryRecord;

import java.util.function.Consumer;

/**
 * Entry point for redacting stored payloads through guardrails-service. Everything is off, and the
 * original behaviour is untouched, unless AKTO_GUARDRAIL_REDACTION_ENABLED=true and
 * AKTO_GUARDRAIL_REDACTION_URL is set (see {@link RedactionConfig}).
 */
public final class PayloadRedaction {

    private static final Object LOCK = new Object();
    private static volatile boolean initialised;
    private static RedactionConfig config;
    private static GuardrailRedactionClient client;
    private static AgentRecordRedactionQueue agentQueue;

    private PayloadRedaction() {
    }

    /**
     * Stores an agent query record, masked. With redaction off it goes straight to the sink. With it
     * on, it is queued so the caller's thread never waits on guardrails-service; either way the
     * record reaches the sink exactly once.
     */
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

    /** A redactor for one sync, or null when redaction is off. */
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
