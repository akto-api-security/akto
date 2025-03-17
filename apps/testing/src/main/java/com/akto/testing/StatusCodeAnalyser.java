package com.akto.testing;

import com.akto.dto.*;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.store.SampleMessageStore;
import java.util.*;

public class StatusCodeAnalyser {

    public static Map<String, String> findAllHosts(SampleMessageStore sampleMessageStore, Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap){
        Map<String, String> hostAndContentType = new HashMap<>();
        for (ApiInfo.ApiInfoKey apiInfoKey: sampleDataMap.keySet()) {
            String host;
            String contentType;
            try {
                loggerMaker.infoAndAddToDb("Finding host for apiInfoKey: " + apiInfoKey.toString());
                OriginalHttpRequest request = TestExecutor.findOriginalHttpRequest(apiInfoKey, sampleDataMap, sampleMessageStore);
                host = TestExecutor.findHostFromOriginalHttpRequest(request);
                contentType = TestExecutor.findContentTypeFromOriginalHttpRequest(request);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error while finding host in status code analyser: " + e, LogDb.TESTING);
                continue;
            }
            if(host != null ){
                hostAndContentType.put(host, contentType);
            }
        }
        return hostAndContentType;
    }

    private static final LoggerMaker loggerMaker = new LoggerMaker(StatusCodeAnalyser.class);
}
