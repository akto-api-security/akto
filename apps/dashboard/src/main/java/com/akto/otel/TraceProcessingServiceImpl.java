package com.akto.otel;

import com.akto.dao.context.Context;
import com.akto.dto.APIConfig;
import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.runtime.Main;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TraceProcessingServiceImpl implements TraceProcessingService {

    private static final LoggerMaker logger = new LoggerMaker(TraceProcessingServiceImpl.class, LoggerMaker.LogDb.DASHBOARD);

    @Override
    public void processTraces(List<HttpResponseParams> traces, int accountId) throws Exception {
        try {
            Context.accountId.set(accountId);

            Map<String, List<HttpResponseParams>> responseParamsToAccountIdMap = new HashMap<>();
            responseParamsToAccountIdMap.put(String.valueOf(accountId), traces);

            APIConfig apiConfig = new APIConfig();

            Main.handleResponseParams(
                responseParamsToAccountIdMap,
                new HashMap<>(),
                false,
                new HashMap<>(),
                apiConfig,
                false,
                true
            );

            logger.infoAndAddToDb("Successfully processed " + traces.size() + " converted traces through API pipeline");
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Failed to process converted traces: " + e.getMessage());
            throw e;
        }
    }
}
