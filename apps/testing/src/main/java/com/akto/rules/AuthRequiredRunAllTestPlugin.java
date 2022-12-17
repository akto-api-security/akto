package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.store.TestingUtil;

import java.util.List;

public abstract class AuthRequiredRunAllTestPlugin extends AuthRequiredTestPlugin {
    @Override
    public Result exec(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil, List<RawApi> filteredMessages) {

        boolean vulnerable = false;
        List<ExecutorResult> results = null;

        for (RawApi rawApi: filteredMessages) {
            if (vulnerable) break;
            results = execute(rawApi, apiInfoKey, testingUtil);
            for (ExecutorResult result: results) {
                if (result.vulnerable) {
                    vulnerable = true;
                    break;
                }
            }
        }

        if (results == null) return null;

        return convertExecutorResultsToResult(results);

    }

    public abstract List<ExecutorResult> execute(RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey , TestingUtil testingUtil);

}
