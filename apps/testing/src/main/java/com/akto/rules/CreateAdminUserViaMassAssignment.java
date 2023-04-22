package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.TestResult;
import com.akto.dto.type.RequestTemplate;
import com.akto.log.LoggerMaker;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.util.JSONUtils;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class CreateAdminUserViaMassAssignment extends TestPlugin{

    private final String testRunId;
    private final String testRunResultSummaryId;
    Logger logger = LoggerFactory.getLogger(CreateAdminUserViaMassAssignment.class);
    private static final Set<String> allowedMethods = new HashSet<>(Arrays.asList("POST", "PUT", "PATCH"));
    private static final List<String> allowedEndpointNames = Arrays.asList("users", "Users", "user", "User");
    private static final List<String> allowedKeysInRequest = Arrays.asList("email", "login", "email_id", "emailid", "email-id");
    private static final List<String> allowedKeysInResponse = Arrays.asList("role", "Role", "user_role", "user_type");

    public CreateAdminUserViaMassAssignment(String testRunId, String testRunResultSummaryId) {
        this.testRunId = testRunId;
        this.testRunResultSummaryId = testRunResultSummaryId;
    }

    @Override
    public Result start(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) {
        List<RawApi> messages = testingUtil.getSampleMessageStore().fetchAllOriginalMessages(apiInfoKey);
        RawApi rawApi = null;
        for(RawApi message: messages) {
            rawApi = message.copy();
            OriginalHttpRequest request = rawApi.getRequest();
            OriginalHttpResponse response = rawApi.getResponse();
            Set<String> extraParams;
            Optional<String> validationResult = checkIfApiIsEligibleForTest(request, response);
            if(validationResult.isPresent()){
                logger.info("Skipping api: {} due to: {}", request.getUrl(), validationResult.get());
                rawApi = null;
                continue;
            }
            extraParams = checkForExtraParams(request, response);
            if(extraParams == null || extraParams.isEmpty()){
                logger.info("Skipping api: {} due to: no extra params found in response", request.getUrl());
                continue;
            }
            String body = createBodyForTest(request, response, extraParams);
            if(StringUtils.isEmpty(body)){
                logger.info("Skipping api: {} due to empty modified body", request.getUrl());
                continue;
            }
            request.setBody(body);
            break;
        }
        if (rawApi == null) return null;

        logger.info("Request: {}", rawApi.getRequest().toString());
        String templateUrl = "https://raw.githubusercontent.com/akto-api-security/tests-library/feature/create-admin-via-mass-assignment/Mass%20Assignment/bussiness-logic/create_admin_user.yaml";

        String testSourceConfigCategory = "";

        Map<String, Object> valuesMap = new HashMap<>();
        valuesMap.put("Method", apiInfoKey.method);
        String baseUrl;
        try {
            baseUrl = rawApi.getRequest().getFullUrlIncludingDomain();
            baseUrl = OriginalHttpRequest.getFullUrlWithParams(baseUrl,rawApi.getRequest().getQueryParams());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while getting full url including domain: " + e, LoggerMaker.LogDb.TESTING);
            return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.FAILED_BUILDING_URL_WITH_DOMAIN,rawApi.getRequest(), null);
        }

        valuesMap.put("BaseURL", baseUrl);
        valuesMap.put("Body", rawApi.getRequest().getBody() == null ? "" : rawApi.getRequest().getBody());

        FuzzingTest fuzzingTest = new FuzzingTest(
                testRunId, testRunResultSummaryId, templateUrl,subTestName(), testSourceConfigCategory, valuesMap
        );
        try {
            return fuzzingTest.runNucleiTest(rawApi);
        } catch (Exception e ) {
            return null;
        }

    }

    private String createBodyForTest(OriginalHttpRequest request, OriginalHttpResponse response, Set<String> extraParams) {
        String jsonRequestBody = request.getJsonRequestBody();
        BasicDBObject jsonRequestBodyObj = BasicDBObject.parse(jsonRequestBody);
        //Update the email id to a random one
        for(String key: allowedKeysInRequest){
            if(jsonRequestBodyObj.containsField(key)){
                String email = jsonRequestBodyObj.getString(key);
                jsonRequestBodyObj.put(key, UUID.randomUUID() + "@" + email.split("@")[1]);
                logger.info("Updated the email id to: {}", jsonRequestBodyObj.get(key));
                break;
            }
        }
        // For now, we are only placing the role related param at the 1st level, this might need to be changed in the future.
        jsonRequestBodyObj.put("{{role_key}}", "{{role_value}}");
        return jsonRequestBodyObj.toJson();
    }

    protected Optional<String> checkIfApiIsEligibleForTest(OriginalHttpRequest request, OriginalHttpResponse response){
        if(response.getStatusCode() < 200 && response.getStatusCode() >= 300){
            return Optional.of("Response code is not 200");
        }
        if(!allowedMethods.contains(request.getMethod())){
            return Optional.of("Request type is not allowed");
        }
        if(StringUtils.isEmpty(request.getBody())){
            return Optional.of("Request body is empty");
        }
        if(StringUtils.isEmpty(response.getBody())){
            return Optional.of("Response body is empty");
        }
        boolean endpointNameMatched = stringMatchContains(request.getUrl(), allowedEndpointNames);
        if(!endpointNameMatched){
            return Optional.of("Doesn't match any of the allowed endpoint names");
        }
        boolean reqBodyContainsRequiredKeys = stringMatchContains(request.getBody(), allowedKeysInRequest);
        if(!reqBodyContainsRequiredKeys){
            return Optional.of("Request body doesn't contain any of the allowed keys");
        }
        boolean respBodyContainsRequiredKeys = stringMatchContains(response.getBody(), allowedKeysInResponse);
        if(!respBodyContainsRequiredKeys){
            return Optional.of("Response body doesn't contain any of the allowed keys");
        }
        return Optional.empty();
    }

    private static boolean stringMatchContains(String str, List<String> allowedStrings) {
        boolean stringMatched = false;
        for(String endpointName: allowedStrings){
            if (str.contains(endpointName)) {
                stringMatched = true;
                break;
            }
        }
        return stringMatched;
    }

    private Set<String> checkForExtraParams(OriginalHttpRequest request, OriginalHttpResponse response){
        BasicDBObject flattenedRequest =  JSONUtils.flattenWithDots(RequestTemplate.parseRequestPayload(request.getJsonRequestBody(), null));
        BasicDBObject flattenedResponse = JSONUtils.flattenWithDots(RequestTemplate.parseRequestPayload(response.getJsonResponseBody(), null));
        if(flattenedRequest.keySet().size() > flattenedResponse.keySet().size()){
            logger.info("Request body has more params than response body");
            return null;
        }
        Set<String> extraParams = new HashSet<>();
        for(String responseKey: flattenedResponse.keySet()){
            String[] splitResponseKeys = responseKey.split("\\.");
            for(String splitResponseKey: splitResponseKeys){
                for(String requestKey: flattenedRequest.keySet()){
                    if(!requestKey.contains(splitResponseKey)){
                        extraParams.add(splitResponseKey);
                    }
                }
            }
        }
        return extraParams;
    }

    @Override
    public String superTestName() {
        return GlobalEnums.TestCategory.MA.toString();
    }

    @Override
    public String subTestName() {
        return GlobalEnums.TestSubCategory.MASS_ASSIGNMENT_CREATE_ADMIN_ROLE.toString();
    }
}
