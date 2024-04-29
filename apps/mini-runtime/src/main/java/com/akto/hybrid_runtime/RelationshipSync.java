package com.akto.hybrid_runtime;

import com.akto.dao.RelationshipDao;
import com.akto.dao.context.Context;
import com.akto.dto.Relationship;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.HttpRequestParams;

import java.util.*;

public class RelationshipSync {
    public final Map<String,Relationship> relations = new HashMap<>();
    private final int user_thresh;
    private int counter;
    private final int counter_thresh;
    private int last_sync;
    private final int last_sync_thresh;
    public Map<String,Map<String, Map<String, Set<Relationship.ApiRelationInfo>>>> userWiseParameterMap = new HashMap<>();
    static ObjectMapper mapper = new ObjectMapper();
    static JsonFactory factory = mapper.getFactory();
    private static final LoggerMaker loggerMaker = new LoggerMaker(RelationshipSync.class);

    public RelationshipSync(int user_thresh, int counter_thresh, int last_sync_thresh) {
        this.user_thresh = user_thresh;
        this.last_sync = Context.now();
        this.counter = 0;
        this.counter_thresh = counter_thresh;
        this.last_sync_thresh = last_sync_thresh;
    }


    public void buildRelationships() {
        for (String userID: userWiseParameterMap.keySet()) {
            Map<String, Map<String, Set<Relationship.ApiRelationInfo>>> valueMap = userWiseParameterMap.get(userID);
            for (String value: valueMap.keySet()) {
                Map<String, Set<Relationship.ApiRelationInfo>> b = valueMap.get(value);
                Set<Relationship.ApiRelationInfo> req = b.get("request");
                Set<Relationship.ApiRelationInfo> res = b.get("response");

                for (Relationship.ApiRelationInfo child: req) {
                    for (Relationship.ApiRelationInfo parent: res) {
                        if (!parent.getUrl().equalsIgnoreCase(child.getUrl()) && uniquenessDetermineFunction(value)) {
                            String key = parent.hashCode() + "." + child.hashCode();
                            Relationship relationship = relations.get(key);
                            if (relationship == null) {
                                relationship = new Relationship(
                                        parent,child, new HashSet<>(), new HashMap<>()
                                );
                            }
                            int count = relationship.getCountMap().getOrDefault(Flow.calculateTodayKey(),0);
                            relationship.getCountMap().put(Flow.calculateTodayKey(), count+1);
                            relationship.getUserIds().add(userID);
                            relations.put(key, relationship);
                        }
                    }
                }
            }
        }


    }

    public List<WriteModel<Relationship>> getBulkUpdates() {
        List<WriteModel<Relationship>> bulkUpdates = new ArrayList<>();
        String todayKey = Flow.calculateTodayKey();

        for (String key: relations.keySet()) {
            Relationship relation = relations.get(key);
            if (relation.getUserIds().size() < user_thresh) {
                continue;
            }
            Bson filters = Filters.and(
                    Filters.eq("parent.url", relation.getParent().getUrl()),
                    Filters.eq("parent.method", relation.getParent().getMethod()),
                    Filters.eq("parent.param", relation.getParent().getParam()),
                    Filters.eq("parent.isHeader", relation.getParent().isHeader()),
                    Filters.eq("parent.responseCode", relation.getParent().getResponseCode()),
                    Filters.eq("child.url", relation.getChild().getUrl()),
                    Filters.eq("child.method", relation.getChild().getMethod()),
                    Filters.eq("child.param", relation.getChild().getParam()),
                    Filters.eq("child.isHeader", relation.getChild().isHeader()),
                    Filters.eq("child.responseCode", relation.getParent().getResponseCode())
            );

            String countFieldName =  "countMap." + todayKey;
            int count = relation.getCountMap().get(todayKey);
            Bson update = Updates.inc(countFieldName, count);

            bulkUpdates.add(
                    new UpdateOneModel<>(filters, update, new UpdateOptions().upsert(true))
            );
            relation.setUserIds(new HashSet<>());
            relation.setCountMap(new HashMap<>());
        }

        return bulkUpdates;
    }

    private void syncWithDb() {
        List<WriteModel<Relationship>> bulkUpdates = getBulkUpdates();
        loggerMaker.infoAndAddToDb("adding " + bulkUpdates.size() + " updates", LogDb.RUNTIME);
        if (bulkUpdates.size() > 0) {
            try {
                RelationshipDao.instance.getMCollection().bulkWrite(bulkUpdates);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e.getMessage(), LogDb.RUNTIME);
            }
        }


    }

    public static boolean uniquenessDetermineFunction(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) return false;
        try {
            double a = Double.parseDouble(value);
            if (a < 10) return false;
            if (a < 100 && a % 10 == 0) return false;
        } catch (NumberFormatException ignored) {

        }
        return true;
    }

    public void init(List<HttpResponseParams> httpResponseParams, String userIdentifierName) {
        for (HttpResponseParams httpResponseParam: httpResponseParams) {
            try {
                buildParameterMap(httpResponseParam, userIdentifierName);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e.getMessage(), LogDb.RUNTIME);
                continue;
            }
            counter += 1;
        }

        if (counter >= this.counter_thresh || (Context.now() - this.last_sync >= this.last_sync_thresh) ) {
            buildRelationships();
            syncWithDb();
            counter = 0;
            last_sync = Context.now();
            userWiseParameterMap = new HashMap<>();
        }
    }

    public void buildParameterMap(HttpResponseParams httpResponseParams, String userIdentifierName) throws Exception {
        HttpRequestParams httpRequestParams = httpResponseParams.getRequestParams();
        String userIdentifier = Flow.getUserIdentifier(userIdentifierName, httpRequestParams);

        String path = httpRequestParams.getURL();
        String method = httpRequestParams.getMethod();
        int responseCode = httpResponseParams.getStatusCode();

        Map<String, Map<String, Set<Relationship.ApiRelationInfo>>> m = userWiseParameterMap.getOrDefault(userIdentifier, new HashMap<>());

        JsonParser jp = factory.createParser(httpRequestParams.getPayload());
        JsonNode node = mapper.readTree(jp);
        Map<String,Set<String>> requestParamMap = new HashMap<>();
        extractAllValuesFromPayload(node,new ArrayList<>(), requestParamMap);


        String respPayload  = httpResponseParams.getPayload();
        if (respPayload.startsWith("[")) {
            respPayload = "{\"json\": "+respPayload+"}";
        }
        jp = factory.createParser(respPayload);
        node = mapper.readTree(jp);
        Map<String,Set<String>> responseParamMap = new HashMap<>();
        extractAllValuesFromPayload(node,new ArrayList<>(), responseParamMap);

        for (String param: responseParamMap.keySet()) {
            Set<String> fieldValueSet = responseParamMap.get(param);
            for (String fieldValue: fieldValueSet) {
                Map<String, Set<Relationship.ApiRelationInfo>> value = m.get(fieldValue);
                if (value == null) {
                    value = new HashMap<>();
                    value.put("request", new HashSet<>());
                    value.put("response", new HashSet<>());
                }

                value.get("response").add(new Relationship.ApiRelationInfo(path,method,false,param,responseCode));
                m.put(fieldValue,value);
            }
        }

        for (String param: requestParamMap.keySet()) {
            Set<String > fieldValueSet = requestParamMap.get(param);
            for (String fieldValue: fieldValueSet) {
                Map<String, Set<Relationship.ApiRelationInfo>> value = m.get(fieldValue);
                if (value == null) {
                    value = new HashMap<>();
                    value.put("request", new HashSet<>());
                    value.put("response", new HashSet<>());
                }

                value.get("request").add(new Relationship.ApiRelationInfo(path,method,false,param,responseCode));
                m.put(fieldValue,value);
            }
        }

        userWiseParameterMap.put(userIdentifier,m);

    }

    public static Map<String, Set<String>> extractAllValuesFromPayload(String payload) {
        if (payload == null || payload.isEmpty()) payload = "{}";
        if (payload.startsWith("[")) payload = "{\"json\": "+payload+"}";

        Map<String, Set<String>> valuesMap = new HashMap<>();
        try {
            JsonParser jp = factory.createParser(payload);
            JsonNode jsonNode = mapper.readTree(jp);
            RelationshipSync.extractAllValuesFromPayload(jsonNode, new ArrayList<>(), valuesMap);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return valuesMap;
    }


    public static void extractAllValuesFromPayload(JsonNode node, List<String> params, Map<String, Set<String>> values) {
        // TODO: null values remove
        if (node == null) return;
        if (node.isValueNode()) {
            String textValue = node.asText();
            if (textValue != null) {
                String param = String.join("",params);
                if (param.startsWith("#")) {
                    param = param.substring(1);
                }
                if (!values.containsKey(param)) {
                    values.put(param, new HashSet<>());
                }
                values.get(param).add(textValue);
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for(int i = 0; i < arrayNode.size(); i++) {
                JsonNode arrayElement = arrayNode.get(i);
                params.add("#$");
                extractAllValuesFromPayload(arrayElement, params, values);
                params.remove(params.size()-1);
            }
        } else {
            Iterator<String> fieldNames = node.fieldNames();
            while(fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                params.add("#"+fieldName);
                JsonNode fieldValue = node.get(fieldName);
                extractAllValuesFromPayload(fieldValue, params,values);
                params.remove(params.size()-1);
            }
        }

    }

    public static boolean checkIfValidText(String text) {
        if (text == null) return false;
        String trimmedText = text.trim();

        if (trimmedText.length() == 0) return false;
        if (trimmedText.equalsIgnoreCase("null")) return false;

        return true;
    }
}
