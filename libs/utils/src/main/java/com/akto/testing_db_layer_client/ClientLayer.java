package com.akto.testing_db_layer_client;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.sql.SampleDataAlt;
import com.akto.dto.sql.SampleDataAltCopy;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClientLayer {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ClientLayer.class, LogDb.TESTING);
    private static final String url = System.getenv("TESTING_DB_LAYER_SERVICE_URL");
    private static final boolean dbMergingMode = System.getenv().getOrDefault("DB_MERGING_MODE", "false").equals("true");
    private static final Gson gson = new Gson();
    ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false).configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);

    public List<String> fetchSamples(ApiInfo.ApiInfoKey apiInfoKey) {
        Map<String, List<String>> headers = new HashMap<>();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiInfoKey.getApiCollectionId());
        obj.put("method", apiInfoKey.getMethod().toString());
        obj.put("url", apiInfoKey.getUrl());
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchSamples", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequestBackOff(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchSamples");
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                return (List) payloadObj.get("samples");
            } catch(Exception e) {
                e.printStackTrace();
                loggerMaker.errorAndAddToDb(e, "error parsing fetchSamples response" + e.getMessage());
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in fetchSamples " + e.getMessage());
            return null;
        }
    }

    public String fetchLatestSample(ApiInfo.ApiInfoKey apiInfoKey) {
        Map<String, List<String>> headers = new HashMap<>();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiInfoKey.getApiCollectionId());
        obj.put("method", apiInfoKey.getMethod().toString());
        obj.put("url", apiInfoKey.getUrl());
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchLatestSample", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequestBackOff(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchLatestSample");
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                return payloadObj.getString("sample");
            } catch(Exception e) {
                e.printStackTrace();
                loggerMaker.errorAndAddToDb(e, "error parsing fetchLatestSample response " + e.getMessage());
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in fetchLatestSample " + e.getMessage());
            return null;
        }
    }

    public int fetchTotalRecords() {
        Map<String, List<String>> headers = new HashMap<>();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchTotalRecords", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequestBackOff(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchTotalRecords");
                return 0;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                int cnt = Integer.parseInt((String) payloadObj.get("records").toString());
                return cnt;
            } catch(Exception e) {
                e.printStackTrace();
                loggerMaker.errorAndAddToDb(e, "error parsing fetchTotalRecords response " + e.getMessage());
                return 0;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in fetchTotalRecords " + e.getMessage());
            return 0;
        }
    }

    public long fetchTotalSize() {
        Map<String, List<String>> headers = new HashMap<>();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchTotalSize", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequestBackOff(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchTotalSize");
                return 0;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                int cnt = Integer.parseInt((String) payloadObj.get("totalSize").toString());
                return Long.valueOf(cnt);
            } catch(Exception e) {
                e.printStackTrace();
                loggerMaker.errorAndAddToDb(e, "error parsing fetchTotalSize response " + e.getMessage());
                return 0;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in fetchTotalSize " + e.getMessage());
            return 0;
        }
    }

    public BasicDBList triggerPostgresCommand(String command) {
        Map<String, List<String>> headers = new HashMap<>();
        BasicDBObject obj = new BasicDBObject();
        obj.put("command", command);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/triggerPostgresCommand", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequestBackOff(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in triggerPostgresCommand");
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList respList = (BasicDBList) payloadObj.get("respList");
                return respList;
            } catch(Exception e) {
                e.printStackTrace();
                loggerMaker.errorAndAddToDb(e, "error parsing triggerPostgresCommand response " + e.getMessage());
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in triggerPostgresCommand " + e.getMessage());
            return null;
        }
    }

    public void bulkInsertSamples(List<SampleDataAlt> unfilteredSamples) {
        Map<String, List<String>> headers = new HashMap<>();
        BasicDBObject obj = new BasicDBObject();
        List<SampleDataAltCopy> samples = new ArrayList<>();
        for (SampleDataAlt sampleDataAlt: unfilteredSamples) {
            samples.add(new SampleDataAltCopy(sampleDataAlt.getId().toString(), sampleDataAlt.getSample(), sampleDataAlt.getApiCollectionId(),sampleDataAlt.getMethod(), sampleDataAlt.getUrl(), sampleDataAlt.getResponseCode(), sampleDataAlt.getTimestamp(), sampleDataAlt.getAccountId()));
        }
        obj.put("samplesCopy", samples);
        String objString = gson.toJson(obj);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/bulkInsertSamples", "", "POST", objString, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequestBackOff(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in bulkInsertSamples");
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in bulkInsertSamples " + e.getMessage());
        }
    }

    public void updateUrl(List<String> uuidList, String newUrl) {
        Map<String, List<String>> headers = new HashMap<>();
        BasicDBObject obj = new BasicDBObject();
        obj.put("uuidList", uuidList);
        obj.put("newUrl", newUrl);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateUrl", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequestBackOff(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateUrl");
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in updateUrl " + e.getMessage());
        }
    }

    public List<SampleDataAlt> fetchSampleData(int apiCollectionId, int offset) {
        Map<String, List<String>> headers = new HashMap<>();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiCollectionId);
        obj.put("offset", offset);
        obj.put("dbMergingMode", dbMergingMode);
        List<SampleDataAlt> results = new ArrayList<>();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchSampleData", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequestBackOff(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchSampleData");
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList samples = (BasicDBList) payloadObj.get("samplesCopy");
                for (Object sample: samples) {
                    BasicDBObject obj2 = (BasicDBObject) sample;
                    //String id = obj2.getString("id");
                    //obj2.put("id", UUID.fromString(id));
                    SampleDataAltCopy s = objectMapper.readValue(obj2.toJson(), SampleDataAltCopy.class);
                    results.add(new SampleDataAlt(UUID.fromString(s.getId()), s.getSample(), s.getApiCollectionId(), 
                        s.getMethod(), s.getUrl(), s.getResponseCode(), s.getTimestamp(), s.getAccountId()));
                }
                return results;
            } catch(Exception e) {
                e.printStackTrace();
                loggerMaker.errorAndAddToDb(e, "error parsing fetchSampleData response " + e.getMessage());
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in fetchSampleData " + e.getMessage());
            return null;
        }
    }

}
