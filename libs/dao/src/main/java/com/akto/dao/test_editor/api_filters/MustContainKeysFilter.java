package com.akto.dao.test_editor.api_filters;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.akto.dao.test_editor.Utils;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ApiSelectionFilters;
import com.akto.dto.test_editor.MustContainKeys;
import com.akto.dto.test_editor.TestConfig;
import com.akto.util.HttpRequestResponseUtils;

public class MustContainKeysFilter extends ApiFilter {

    @Override
    public boolean isValid(TestConfig testConfig, RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey) {

        ApiSelectionFilters filters = testConfig.getApiSelectionFilters();
        if (filters == null) {
            return true;
        }
        List<MustContainKeys> mustContainKeyFilter = filters.getMustContainKeys();
        OriginalHttpRequest originalHttpRequest = rawApi.getRequest().copy();
        OriginalHttpResponse originalHttpResponse = rawApi.getResponse().copy();
        Boolean found = false;
        
        if (mustContainKeyFilter == null || mustContainKeyFilter.size() == 0) {
            return true;
        }

        for (MustContainKeys mustContainKeys: mustContainKeyFilter) {
            for (String key: mustContainKeys.getKeyRegex()) {
                found = false;
                for (String loc : mustContainKeys.getLocation()) {
                    if (loc.equals("request-body")) {
                        found = Utils.checkIfContainsMatch(originalHttpRequest.getBody(), key);
                        if (found) {
                            break;
                        }
                    } else if (loc.equals("response-body")) {
                        found = Utils.checkIfContainsMatch(originalHttpResponse.getBody(), key);
                        if (found) {
                            return true;
                        }
                    } else if (loc.equals("request-header")) {
                        for (String headerName: originalHttpRequest.getHeaders().keySet()) {
                            found = Utils.checkIfContainsMatch(headerName, key);
                            if (found) {
                                break;
                            }
                        }
                    } else if (loc.equals("response-header")) {
                        for (String headerName: originalHttpResponse.getHeaders().keySet()) {
                            found = Utils.checkIfContainsMatch(headerName, key);
                            if (found) {
                                break;
                            }
                        }
                    } else if (loc.equals("queryParam")) {
                        String queryJson = HttpRequestResponseUtils.convertFormUrlEncodedToJson(originalHttpRequest.getQueryParams());
                        if (queryJson == null) {
                            continue;
                        }
                        found = Utils.checkIfContainsMatch(queryJson, key);
                        if (found) {
                            break;
                        }
                    }
                }

                if (found) {
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }

        return found;
    }

}
