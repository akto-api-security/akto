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
import com.akto.rules.TestPlugin;
import com.akto.runtime.policies.ApiAccessTypePolicy;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;

public class HttpCallFilter {

    private FilterConfig apiFilters;
    private List<WriteModel<SusSampleData>> susSampleDataList;
    private final int sync_threshold_count;
    private final int sync_threshold_time;
    private int last_synced;
    private int sync_count;
    private int numberOfSyncs;

    public HttpCallFilter(int sync_threshold_count, int sync_threshold_time) {
        apiFilters = FilterYamlTemplateDao.instance.fetchFilterConfig();
        susSampleDataList = new ArrayList<>();
        this.sync_threshold_count = sync_threshold_count;
        this.sync_threshold_time = sync_threshold_time;
        last_synced = 0;
        sync_count = 0;
        numberOfSyncs = 0;
    }

    public void filterFunction(List<HttpResponseParams> responseParams) {

        for (HttpResponseParams responseParam : responseParams) {
            String message = responseParam.getOrig();
            List<String> sourceIps = ApiAccessTypePolicy.getSourceIps(responseParam);
            RawApi rawApi = RawApi.buildFromMessage(message);
            int apiCollectionId = responseParam.getRequestParams().getApiCollectionId();
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
                    apiInfoKey,
                    varMap, filterExecutionLogId);
            if (res.getIsValid()) {
                int now = Context.now();
                SusSampleData sampleData = new SusSampleData(sourceIps, apiCollectionId, url, method, message, now);
                susSampleDataList.add(new InsertOneModel<SusSampleData>(sampleData));
            }

        }

        this.sync_count = susSampleDataList.size();
        int syncThresh = numberOfSyncs < 10 ? 50 : sync_threshold_count;

        if (this.sync_count >= syncThresh || (Context.now() - this.last_synced) > this.sync_threshold_time) {

            SusSampleDataDao.instance.getMCollection().bulkWrite(susSampleDataList);

            this.last_synced = Context.now();
            this.sync_count = 0;
            susSampleDataList.clear();
        }

    }

}
