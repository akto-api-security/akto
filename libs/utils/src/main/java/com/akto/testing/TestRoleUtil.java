package com.akto.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dto.*;

import com.akto.dao.common.AuthPolicy;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.HardcodedAuthParam;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.dto.traffic.SampleData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.akto.util.Constants.default_token;

public class TestRoleUtil {

        private static final DataActor dataActor = DataActorFactory.fetchInstance();

        private static final Logger log = LoggerFactory.getLogger(TestRoles.class);

    public static AuthMechanism findDefaultAuthMechanism(TestRoles testRole) {
        try {
            for(AuthWithCond authWithCond: testRole.getAuthWithCondList()) {
                if (authWithCond.getHeaderKVPairs().isEmpty()) {
                    AuthMechanism ret = authWithCond.getAuthMechanism();
                    if(authWithCond.getRecordedLoginFlowInput()!=null){
                        ret.setRecordedLoginFlowInput(authWithCond.getRecordedLoginFlowInput());
                    }

                    return ret;
                }
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    public static AuthMechanism findMatchingAuthMechanism(TestRoles testRole, RawApi rawApi) {
        if (rawApi == null) {
            return findDefaultAuthMechanism(testRole);
        }

        String deafaultAuthHeader = "";
        try {
            List<AuthWithCond> authWithConds = testRole.getAuthWithCondList();
            for (AuthWithCond authWithCond : authWithConds) {
                List<AuthParam> params = authWithCond.getAuthMechanism().getAuthParams();
                for (AuthParam param : params) {
                    if (param.getKey().equals("Authorization") && param.getValue() != null) {
                        deafaultAuthHeader = param.getValue();
                        break;
                    }
                }
            }
        }catch (Exception e){
            return findDefaultAuthMechanism(testRole);
        }
        if (deafaultAuthHeader.equals(default_token)) {

            try {

                HttpResponseParams httpResponseParams = createResponseParamsFromRawApi(rawApi);

                Set<Set<ApiInfo.AuthType>> allAuthTypesFound = new HashSet<>();
                ApiInfo apiInfo= new ApiInfo(ApiInfo.ApiType.REST, allAuthTypesFound);

                Map<String, List<String>> headers = rawApi.fetchReqHeaders();
                List<String> headerKeys = new ArrayList<>(headers.keySet());
                List<CustomAuthType> customAuthTypes = new ArrayList<>();
                for (String headerKey : headerKeys) {
                    CustomAuthType customAuthType = new CustomAuthType();
                    customAuthType.setHeaderKeys(Collections.singletonList(headerKey));
                    customAuthTypes.add(customAuthType);
                }

                AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);

                Map<String, String> headersMap = AuthPolicy.headersMap;
                List<String> authHeaders = AuthPolicy.authHeaders;
                if (authHeaders == null || authHeaders.isEmpty()) {
                    return findDefaultAuthMechanism(testRole);
                }


                // TODO
                ApiInfo apiInfoLatest = dataActor.fetchLatestAuthenticatedByApiCollectionId(rawApi.getRawApiMetadata().getApiCollectionId());
                String latestUrl = rawApi.getRequest().getUrl();
                if( apiInfoLatest != null)
                    latestUrl = apiInfoLatest.getId().getUrl();

                SampleData sampleData = dataActor.fetchSampleDataByIdMethod(rawApi.getRawApiMetadata().getApiCollectionId(), latestUrl, rawApi.getRequest().getMethod());

                if (sampleData == null || sampleData.getSamples() == null || sampleData.getSamples().isEmpty()) {
                    return findDefaultAuthMechanism(testRole);
                }

                List<String> sampleTokens = new ArrayList<>();
                List<String> samples = sampleData.getSamples();
                if (samples != null && !samples.isEmpty()) {
                    for(String sample : samples){
                        HttpResponseParams sampleResponse = AuthPolicy.parseSampleData(sample);
                        if (sampleResponse.getRequestParams() == null) {
                            continue;
                        }else{
                            String token = sampleResponse.getRequestParams().getHeaders().getOrDefault(authHeaders.get(0), new ArrayList<>()).stream()
                                    .findFirst()
                                    .orElse(null);

                            sampleTokens.add(token);
                        }
                    }
                }

                List<AuthParam> authParams = new ArrayList<>();

                for(String authHeader: authHeaders) {
                    String tokenToBeUsed = headersMap.get(authHeader);
                    if(!sampleTokens.isEmpty()) {
                        String rawApiToken = headersMap.get(authHeader);
                        // Check if the token is in the sample tokens
                        boolean isSampleToken = sampleTokens.stream().anyMatch(token -> token.equals(rawApiToken));
                        if (!isSampleToken) {
                            continue;
                        }else {
                            sampleTokens.remove(rawApiToken);
                            List<String> updatedSampleTokens = new ArrayList<>(sampleTokens);
                            tokenToBeUsed = updatedSampleTokens.isEmpty() ? rawApiToken : updatedSampleTokens.get(0);
                        }
                    }else {
                        tokenToBeUsed = headersMap.get(authHeader);
                    }
                    log.info("Using token: {} for auth header: {}", tokenToBeUsed, authHeader);
                    HardcodedAuthParam hardcodedAuthParam = new HardcodedAuthParam(AuthParam.Location.HEADER, authHeader, tokenToBeUsed, true);
                    authParams.add(hardcodedAuthParam);

                }

                AuthMechanism mechanism = new AuthMechanism();
                mechanism.setAuthParams(authParams);
                if (mechanism != null) {
                    return mechanism;
                }

            } catch (Exception e) {
            }
        } else {

            for (AuthWithCond authWithCond : testRole.getAuthWithCondList()) {

                try {
                    boolean allSatisfied = true;

                    if (authWithCond.getHeaderKVPairs().isEmpty()) {
                        continue;
                    }

                    for (String headerKey : authWithCond.getHeaderKVPairs().keySet()) {
                        String headerVal = authWithCond.getHeaderKVPairs().get(headerKey);
                        List<String> rawHeaderValue = rawApi.getRequest().getHeaders().getOrDefault(headerKey.toLowerCase(), new ArrayList<>());
                        if (!rawHeaderValue.contains(headerVal)) {
                            allSatisfied = false;
                            break;
                        }
                    }

                    if (allSatisfied) {
                        AuthMechanism ret = authWithCond.getAuthMechanism();
                        if (authWithCond.getRecordedLoginFlowInput() != null) {
                            ret.setRecordedLoginFlowInput(authWithCond.getRecordedLoginFlowInput());
                        }
                        return ret;
                    }
                } catch (Exception e) {
                    // Handle exception if needed
                }
            }
        }

        return findDefaultAuthMechanism(testRole);
    }

    public static HttpResponseParams createResponseParamsFromRawApi(RawApi rawApi) {
        if (rawApi == null || rawApi.getResponse() == null) {
            return null;
        }
        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setStatusCode(rawApi.getResponse().getStatusCode());
        responseParams.setHeaders(rawApi.getResponse().getHeaders());
        responseParams.setPayload(rawApi.getResponse().getBody());
        HttpRequestParams requestParams = new HttpRequestParams();
        requestParams.setHeaders(rawApi.fetchReqHeaders());
        responseParams.setRequestParams(requestParams);
        return responseParams;

    }


    
}
