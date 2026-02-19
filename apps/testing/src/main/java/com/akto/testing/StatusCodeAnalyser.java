package com.akto.testing;

import com.akto.DaoInit;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.rules.TestPlugin;
import com.akto.store.SampleMessageStore;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;

import java.util.*;

import static com.akto.runtime.RelationshipSync.extractAllValuesFromPayload;
import static com.akto.testing.TestExecutor.findHost;

public class StatusCodeAnalyser {

    static ObjectMapper mapper = new ObjectMapper();
    static JsonFactory factory = mapper.getFactory();

    static List<StatusCodeIdentifier> result = new ArrayList<>();
    static Map<Integer, Integer> defaultPayloadsMap = new HashMap<>();

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

    public static void run(Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap, SampleMessageStore sampleMessageStore, AuthMechanism authMechanism, TestingRunConfig testingRunConfig) {
        defaultPayloadsMap = new HashMap<>();
        result = new ArrayList<>();
        if (sampleDataMap == null) {
            loggerMaker.errorAndAddToDb("No sample data");
            return;
        }
        loggerMaker.infoAndAddToDb("started calc default payloads");

        calculateDefaultPayloads(sampleMessageStore, sampleDataMap, testingRunConfig);

        loggerMaker.infoAndAddToDb("started fill result");
        fillResult(sampleMessageStore, sampleDataMap, authMechanism, testingRunConfig);
    }

    public static void calculateDefaultPayloads(SampleMessageStore sampleMessageStore, Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap, TestingRunConfig testingRunConfig) {
        Set<String> hosts = new HashSet<>();
        for (ApiInfo.ApiInfoKey apiInfoKey: sampleDataMap.keySet()) {
            String host;
            try {
                 host = findHost(apiInfoKey, sampleDataMap, sampleMessageStore);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error while finding host in status code analyser: " + e.getMessage());
                continue;
            }

            hosts.add(host);
        }

        for (String host: hosts) {
            loggerMaker.infoAndAddToDb("calc default payload for host: " + host, LogDb.TESTING);
            for (int idx=0; idx<11;idx++) {
                try {
                    String url = host;
                    if (!url.endsWith("/")) url += "/";
                    if (idx > 0) url += "akto-"+idx; // we want to hit host url once too

                    OriginalHttpRequest request = new OriginalHttpRequest(url, null, URLMethods.Method.GET.name(), null, new HashMap<>(), "");
                    OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, testingRunConfig, false, new ArrayList<>(), Main.SKIP_SSRF_CHECK);
                    boolean isStatusGood = TestPlugin.isStatusGood(response.getStatusCode());
                    if (!isStatusGood) continue;

                    String body = response.getBody();
                    fillDefaultPayloadsMap(body);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("", LogDb.TESTING);
                }
            }
        }

    }

    public static void fillDefaultPayloadsMap(String body) {
        int hash = body.hashCode();
        Integer count = defaultPayloadsMap.getOrDefault(hash, 0);
        defaultPayloadsMap.put(hash, count+1);
    }

    public static boolean isDefaultPayload(String payload) {
        if (payload == null) return true;
        int hash = payload.hashCode();
        int occurrence = defaultPayloadsMap.getOrDefault(hash, 0);

        return occurrence >= 5;
    }

    private static final LoggerMaker loggerMaker = new LoggerMaker(StatusCodeAnalyser.class);
    public static int MAX_COUNT = 30;
    public static void fillResult(SampleMessageStore sampleMessageStore, Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap, AuthMechanism authMechanism, TestingRunConfig testingRunConfig) {
        loggerMaker.infoAndAddToDb("Running status analyser", LogDb.TESTING);

        if (authMechanism == null) {
            loggerMaker.errorAndAddToDb("No auth mechanism", LogDb.TESTING);
            return;
        }
        Map<Set<String>, Map<String,Integer>> frequencyMap = new HashMap<>();
        Map<Integer, ApiCollection> apiCollectionMap = ApiCollectionsDao.instance.generateApiCollectionMap();

        int count = 0;
        int inc = 0;
        for (ApiInfo.ApiInfoKey apiInfoKey: sampleDataMap.keySet()) {
            ApiCollection apiCollection = apiCollectionMap.get(apiInfoKey.getApiCollectionId());
            if (!apiInfoKey.url.startsWith("http")) { // mirroring
                if (apiCollection == null || apiCollection.getHostName() == null || apiCollection.getHostName().isEmpty()) {
                    continue;
                }
            }

            if (inc >= 5) {
                loggerMaker.infoAndAddToDb("5 error API calls. Exiting status code analyser", LogDb.TESTING);
                break;
            }

            if (count > MAX_COUNT) break;
            try {
                List<RawApi> messages = sampleMessageStore.fetchAllOriginalMessages(apiInfoKey);
                boolean success;
                try {
                    success = fillFrequencyMap(messages, authMechanism, frequencyMap, testingRunConfig);
                } catch (Exception e) {
                    inc += 1;
                    continue;
                }
                if (success)  {
                    count += 1;
                    loggerMaker.infoAndAddToDb("count: " + count, LogDb.TESTING);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error while filling frequency map: " + e, LogDb.TESTING);
            }
        }

        calculateResult(frequencyMap, 5);

        loggerMaker.infoAndAddToDb("result of status code analyser : " + result, LogDb.TESTING);
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

    public static boolean fillFrequencyMap(List<RawApi> messages, AuthMechanism authMechanism,
                                           Map<Set<String>, Map<String,Integer>> frequencyMap, TestingRunConfig testingRunConfig) throws Exception {

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
        OriginalHttpResponse finalResponse = ApiExecutor.sendRequest(request, true, testingRunConfig, false, new ArrayList<>(), Main.SKIP_SSRF_CHECK);

        // if non 2xx then skip this api
        if (finalResponse.getStatusCode() < 200 || finalResponse.getStatusCode() >= 300) return false;

        fillDefaultPayloadsMap(finalResponse.getBody());

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
