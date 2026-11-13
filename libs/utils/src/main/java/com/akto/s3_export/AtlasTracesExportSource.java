package com.akto.s3_export;

import java.util.Map;
import java.util.function.Consumer;

import com.akto.utils.search.SearchClientFactory;

/** Atlas LLM traces, from the search backend (Elasticsearch). */
public class AtlasTracesExportSource implements S3ExportSource {

    @Override
    public void export(int accountId, long startMs, long endMs, boolean includePayloads,
                       Consumer<Map<String, Object>> consumer) throws Exception {
        SearchClientFactory.instance().exportAtlasTraces(accountId, startMs, endMs, includePayloads, consumer);
    }
}
