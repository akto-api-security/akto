package com.akto.filters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.akto.dao.SusSampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.RawApi;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.traffic.SusSampleData;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.rules.TestPlugin;
import com.akto.runtime.policies.ApiAccessTypePolicy;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;

public class HttpCallFilter {
    private static final LoggerMaker loggerMaker = new LoggerMaker(HttpCallFilter.class, LogDb.THREAD_DETECTION);

    private FilterConfig apiFilters;
    private List<WriteModel<SusSampleData>> susSampleDataList;
    private final int sync_threshold_count;
    private final int sync_threshold_time;
    private int last_synced;
    private int sync_count;
    private HttpCallParser httpCallParser;

    private static final int FILTER_REFRESH_INTERVAL = 10 * 60;
    private int lastFilterFetch;

    public HttpCallFilter(int sync_threshold_count, int sync_threshold_time) {
        apiFilters = FilterYamlTemplateDao.instance.fetchFilterConfig(false);
        susSampleDataList = new ArrayList<>();
        this.sync_threshold_count = sync_threshold_count;
        this.sync_threshold_time = sync_threshold_time;
        last_synced = 0;
        sync_count = 0;
        lastFilterFetch = 0;
        httpCallParser = new HttpCallParser(sync_threshold_count, sync_threshold_time);
    }

    public void filterFunction(List<HttpResponseParams> responseParams) {

        int now = Context.now();
        if ((lastFilterFetch + FILTER_REFRESH_INTERVAL) < now) {
            apiFilters = FilterYamlTemplateDao.instance.fetchFilterConfig(false);
            lastFilterFetch = now;
        }

        if (apiFilters != null) {
            for (HttpResponseParams responseParam : responseParams) {
                try {
                    String message = responseParam.getOrig();
                    List<String> sourceIps = ApiAccessTypePolicy.getSourceIps(responseParam);
                    RawApi rawApi = RawApi.buildFromMessage(message);
                    int apiCollectionId = httpCallParser.createApiCollectionId(responseParam);
                    responseParam.requestParams.setApiCollectionId(apiCollectionId);
                    String url = responseParam.getRequestParams().getURL();
                    Method method = Method.valueOf(responseParam.getRequestParams().getMethod());
                    ApiInfoKey apiInfoKey = new ApiInfoKey(apiCollectionId, url, method);
                    Map<String, Object> varMap = apiFilters.resolveVarMap();
                    VariableResolver.resolveWordList(varMap, new HashMap<ApiInfoKey, List<String>>() {
                        {
                            put(apiInfoKey, Arrays.asList(message));
                        }
                    }, apiInfoKey);
                    String filterExecutionLogId = UUID.randomUUID().toString();
                    ValidationResult res = TestPlugin.validateFilter(apiFilters.getFilter().getNode(), rawApi,
                            apiInfoKey, varMap, filterExecutionLogId);
                    if (res.getIsValid()) {
                        now = Context.now();
                        SusSampleData sampleData = new SusSampleData(sourceIps, apiCollectionId, url, method, message,
                                now);
                        susSampleDataList.add(new InsertOneModel<SusSampleData>(sampleData));
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, String.format("Error in httpCallFilter %s", e.toString()));
                }
            }
        }
        sync_count = susSampleDataList.size();
        if (sync_count > 0 && (sync_count >= sync_threshold_count ||
                (Context.now() - last_synced) > sync_threshold_time)) {
            SusSampleDataDao.instance.getMCollection().bulkWrite(susSampleDataList);
            last_synced = Context.now();
            sync_count = 0;
            susSampleDataList.clear();
        }
    }
}