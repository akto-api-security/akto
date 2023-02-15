package com.akto.testing;

import com.akto.dao.AuthMechanismsDao;
import com.akto.dto.*;
import com.akto.dto.testing.AuthMechanism;
import com.akto.store.SampleMessageStore;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.akto.runtime.RelationshipSync.extractAllValuesFromPayload;

public class StatusCodeAnalyser {

    static ObjectMapper mapper = new ObjectMapper();
    static JsonFactory factory = mapper.getFactory();

    static List<StatusCodeIdentifier> result = new ArrayList<>();

    public static class StatusCodeIdentifier {
        public Set<String> keySet;
        public String statusCodeKey;

        public StatusCodeIdentifier(Set<String> keySet, String statusCodeKey) {
            this.keySet = keySet;
            this.statusCodeKey = statusCodeKey;
        }

        @Override
        public String toString() {
            return keySet + "  " + statusCodeKey;
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(StatusCodeAnalyser.class);

    public static int MAX_COUNT = 30;
    public static void run() {
        logger.info("Running status analyser");
        Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap = SampleMessageStore.fetchSampleMessages();

        AuthMechanism authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());
        if (authMechanism == null) {
            logger.error("No auth mechanism");
            return;
        }
        Map<Set<String>, Map<String,Integer>> frequencyMap = new HashMap<>();

        int count = 0;
        int inc = 0;
        for (ApiInfo.ApiInfoKey apiInfoKey: sampleDataMap.keySet()) {
            if (count > MAX_COUNT) break;
            try {
                List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, sampleDataMap);
                boolean success = fillFrequencyMap(messages, authMechanism, frequencyMap);
                if (success)  {
                    count += 1;
                    logger.info("count: " + count);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (inc % 10 == 0) System.out.println(inc);
            inc += 1;
        }

        calculateResult(frequencyMap, 5);

        System.out.println("*********************");
        System.out.println(result);
        System.out.println("*********************");
    }

    public static void calculateResult(Map<Set<String>, Map<String,Integer>> frequencyMap, int threshold) {
        if (frequencyMap == null) return;
        for (Set<String> params: frequencyMap.keySet()) {
            Map<String, Integer> countObj = frequencyMap.getOrDefault(params, new HashMap<>());
            for (String key: countObj.keySet()) {
                if (countObj.get(key) > threshold) {
                    result.add(new StatusCodeIdentifier(params, key));
                    break;
                }
            }
        }
    }

    public static boolean fillFrequencyMap(List<RawApi> messages, AuthMechanism authMechanism, Map<Set<String>, Map<String,Integer>> frequencyMap) {

        // fetch sample message
        List<RawApi> filteredMessages = SampleMessageStore.filterMessagesWithAuthToken(messages, authMechanism);
        if (filteredMessages.isEmpty()) return false;

        RawApi rawApi = filteredMessages.get(0);

        OriginalHttpRequest request = rawApi.getRequest();
        OriginalHttpResponse response = rawApi.getResponse();

        // discard if payload is null or empty
        String originalPayload = response.getBody();
        if (originalPayload == null || originalPayload.equals("{}") || originalPayload.isEmpty()) {
            return false;
        }

        // if auth token is not passed originally -> skip
        boolean result = authMechanism.removeAuthFromRequest(request);
        if (!result) return false;

        // execute API
        OriginalHttpResponse finalResponse;
        try {
            finalResponse = ApiExecutor.sendRequest(request, true);
        } catch (Exception e) {
            return false;
        }

        // if non 2xx then skip this api
        if (finalResponse.getStatusCode() < 200 || finalResponse.getStatusCode() >= 300) return false;

        // store response keys and values in map
        String payload = finalResponse.getBody();
        Map<String, Set<String>> responseParamMap = new HashMap<>();
        Map<String, Set<String>> originalResponseParamMap = new HashMap<>();
        try {
            JsonParser jp = factory.createParser(payload);
            JsonNode node = mapper.readTree(jp);
            extractAllValuesFromPayload(node,new ArrayList<>(), responseParamMap);

            jp = factory.createParser(originalPayload);
            node = mapper.readTree(jp);
            extractAllValuesFromPayload(node,new ArrayList<>(), originalResponseParamMap);
        } catch (Exception e) {
            return false;
        }

        if (responseParamMap.size() > 10) return false;

        List<String> potentialStatusCodeKeys = getPotentialStatusCodeKeys(responseParamMap);
        if (potentialStatusCodeKeys.isEmpty()) return false;

        Set<String> params = responseParamMap.keySet();
        Map<String, Integer> newCountObj = frequencyMap.get(params);
        if (newCountObj != null) {
            for (String statusCodeKey: potentialStatusCodeKeys) {
                Integer val = newCountObj.getOrDefault(statusCodeKey, 0);
                newCountObj.put(statusCodeKey, val + 1);
            }
        } else {
            // Add only if original and current payloads are different
            if (originalResponseParamMap.keySet().equals(responseParamMap.keySet())) {
                return false;
            }
            frequencyMap.put(params, new HashMap<>());
            for (String statusCodeKey: potentialStatusCodeKeys) {
                frequencyMap.get(params).put(statusCodeKey, 1);
            }
        }


        return true;
    }

    public static List<String> getPotentialStatusCodeKeys(Map<String,Set<String>> responseParamMap) {
        List<String> potentialStatusCodeKeys = new ArrayList<>();
        for (String key: responseParamMap.keySet()) {
            Set<String> val = responseParamMap.get(key);
            if (val== null || val.size() != 1) continue;
            List<String> valList = new ArrayList<>(val);
            String statusCodeString = valList.get(0);
            try {
                int statusCode = Integer.parseInt(statusCodeString);
                if (statusCode < 0 || statusCode > 999 ) continue;
            } catch (Exception e) {
                continue;
            }
            potentialStatusCodeKeys.add(key);
            break;
        }

        return potentialStatusCodeKeys;
    }

    public static int getStatusCode(String payload, int statusCode) {
        if (statusCode < 200 || statusCode >= 300) return statusCode;

        Map<String, Set<String>> responseParamMap = new HashMap<>();
        try {
            JsonParser jp = factory.createParser(payload);
            JsonNode node = mapper.readTree(jp);
            extractAllValuesFromPayload(node,new ArrayList<>(), responseParamMap);
        } catch (Exception e) {
            return statusCode;
        }

        for (StatusCodeIdentifier statusCodeIdentifier: result) {
            boolean flag = false;
            for (String key: statusCodeIdentifier.keySet) {
                if (!responseParamMap.containsKey(key)) {
                    flag = true;
                    break;
                }
            }

            if (flag) continue;

            Set<String> val = responseParamMap.get(statusCodeIdentifier.statusCodeKey);
            if (val == null || val.isEmpty()) continue;
            String vv = val.iterator().next();
            try {
                return Integer.parseInt(vv);
            } catch (Exception ignored) { }
        }

        return statusCode;
    }

}
