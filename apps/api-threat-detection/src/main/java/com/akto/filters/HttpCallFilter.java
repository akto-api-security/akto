package com.akto.filters;

import java.util.*;
import java.util.Map.Entry;

import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.RawApi;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.dto.bulk_updates.UpdatePayload;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.traffic.SuspectSampleData;
import com.akto.dto.type.URLMethods.Method;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.malicious_request.MaliciousRequest;
import com.akto.malicious_request.MaliciousRequestGenerator;
import com.akto.rules.TestPlugin;
import com.akto.runtime.policies.ApiAccessTypePolicy;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;
import com.akto.threat.detection.dto.ResponseWrapper;

public class HttpCallFilter {
    private static final LoggerMaker loggerMaker =
            new LoggerMaker(HttpCallFilter.class, LogDb.THREAT_DETECTION);

    private Map<String, FilterConfig> apiFilters;
    private final List<BulkUpdates> bulkUpdates;
    private final int sync_threshold_count;
    private final int sync_threshold_time;
    private int last_synced;
    private int sync_count;
    private final HttpCallParser httpCallParser;
    private static final int FILTER_REFRESH_INTERVAL = 10 * 60;
    private int lastFilterFetch;

    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    public HttpCallFilter(int sync_threshold_count, int sync_threshold_time) {
        this.apiFilters = new HashMap<>();
        this.bulkUpdates = new ArrayList<>();
        this.sync_threshold_count = sync_threshold_count;
        this.sync_threshold_time = sync_threshold_time;
        this.last_synced = 0;
        this.sync_count = 0;
        this.lastFilterFetch = 0;
        this.httpCallParser = new HttpCallParser(sync_threshold_count, sync_threshold_time);
    }

    public ResponseWrapper processRequests(List<HttpResponseParams> responseParams) {

        int now = Context.now();
        if ((lastFilterFetch + FILTER_REFRESH_INTERVAL) < now) {
            // TODO: add support for only active templates.
            List<YamlTemplate> templates = dataActor.fetchFilterYamlTemplates();
            apiFilters = FilterYamlTemplateDao.instance.fetchFilterConfig(false, templates, false);
            lastFilterFetch = now;
        }

        List<MaliciousRequest> maliciousRequests = new ArrayList<>();

        if (apiFilters != null && !apiFilters.isEmpty()) {
            for (HttpResponseParams responseParam : responseParams) {
                for (Entry<String, FilterConfig> apiFilterEntry : apiFilters.entrySet()) {
                    try {
                        FilterConfig apiFilter = apiFilterEntry.getValue();
                        String filterId = apiFilterEntry.getKey();
                        String message = responseParam.getOrig();
                        List<String> sourceIps = ApiAccessTypePolicy.getSourceIps(responseParam);
                        RawApi rawApi = RawApi.buildFromMessage(message);
                        int apiCollectionId = httpCallParser.createApiCollectionId(responseParam);
                        responseParam.requestParams.setApiCollectionId(apiCollectionId);

                        List<MaliciousRequest> maliciousRequestsPerResponseParams =
                                MaliciousRequestGenerator.INSTANCE.generateIfAny(responseParam);

                        boolean isMalicious = !maliciousRequestsPerResponseParams.isEmpty();

                        String url = responseParam.getRequestParams().getURL();
                        Method method =
                                Method.fromString(responseParam.getRequestParams().getMethod());
                        ApiInfoKey apiInfoKey = new ApiInfoKey(apiCollectionId, url, method);
                        Map<String, Object> varMap = apiFilter.resolveVarMap();
                        VariableResolver.resolveWordList(
                                varMap,
                                new HashMap<ApiInfoKey, List<String>>() {
                                    {
                                        put(apiInfoKey, Collections.singletonList(message));
                                    }
                                },
                                apiInfoKey);
                        String filterExecutionLogId = UUID.randomUUID().toString();
                        ValidationResult res =
                                TestPlugin.validateFilter(
                                        apiFilter.getFilter().getNode(),
                                        rawApi,
                                        apiInfoKey,
                                        varMap,
                                        filterExecutionLogId);

                        if (res.getIsValid() || isMalicious) {
                            now = Context.now();
                            SuspectSampleData sampleData =
                                    new SuspectSampleData(
                                            sourceIps,
                                            apiCollectionId,
                                            url,
                                            method,
                                            message,
                                            now,
                                            filterId);
                            Map<String, Object> filterMap = new HashMap<>();
                            UpdatePayload updatePayload =
                                    new UpdatePayload("obj", sampleData, "set");
                            ArrayList<String> updates = new ArrayList<>();
                            updates.add(updatePayload.toString());
                            bulkUpdates.add(new BulkUpdates(filterMap, updates));

                            if (isMalicious) {
                                maliciousRequests.addAll(maliciousRequestsPerResponseParams);
                            }
                        }
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(
                                e, String.format("Error in httpCallFilter %s", e.toString()));
                    }
                }
            }
        }
        sync_count = bulkUpdates.size();
        if (sync_count > 0
                && (sync_count >= sync_threshold_count
                        || (Context.now() - last_synced) > sync_threshold_time)) {
            List<Object> updates = new ArrayList<>(bulkUpdates);
            dataActor.bulkWriteSuspectSampleData(updates);
            loggerMaker.infoAndAddToDb(
                    String.format("Inserting %d records in SuspectSampleData", sync_count));
            last_synced = Context.now();
            sync_count = 0;
            bulkUpdates.clear();
        }

        return ResponseWrapper.builder().withMaliciousRequests(maliciousRequests).build();
    }
}
