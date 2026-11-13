package com.akto.s3_export;

import java.util.Map;
import java.util.function.Consumer;

/** One exportable dataset: streams an account's rows for a time window. */
public interface S3ExportSource {

    /**
     * Streams every row of the account in the half-open window [startMs, endMs) to the consumer,
     * in a deterministic order (a retried window must yield the same rows). Throws on any error.
     */
    void export(int accountId, long startMs, long endMs, boolean includePayloads,
        Consumer<Map<String, Object>> consumer) throws Exception;
}
