package com.akto.action;


import com.akto.DaoInit;
import com.akto.dao.CustomDataTypeDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.CustomDataType;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.User;
import com.akto.dto.data_types.*;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.RelationshipSync;
import com.akto.utils.AktoCustomException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.InsertOneResult;
import com.opensymphony.xwork2.Action;

import org.bson.types.ObjectId;

import java.io.IOException;
import java.util.*;

public class CustomDataTypeAction extends UserAction{

    private boolean createNew;
    private String name;
    private boolean sensitiveAlways;
    private String operator;

    private String keyOperator;
    private List<ConditionFromUser> keyConditionFromUsers;

    private String valueOperator;
    private List<ConditionFromUser> valueConditionFromUsers;

    public static class ConditionFromUser {
        Predicate.Type type;
        Map<String, Object> valueMap;

        public ConditionFromUser(Predicate.Type type, Map<String, Object> valueMap) {
            this.type = type;
            this.valueMap = valueMap;
        }

        public ConditionFromUser() { }

        public Predicate.Type getType() {
            return type;
        }

        public void setType(Predicate.Type type) {
            this.type = type;
        }

        public Map<String, Object> getValueMap() {
            return valueMap;
        }

        public void setValueMap(Map<String, Object> valueMap) {
            this.valueMap = valueMap;
        }
    }


    private static final ObjectMapper mapper = new ObjectMapper();
    private static final JsonFactory factory = mapper.getFactory();


    private BasicDBObject dataTypes;
    public String fetchDataTypes() {
        List<CustomDataType> customDataTypes = CustomDataTypeDao.instance.findAll(new BasicDBObject());
        Collections.reverse(customDataTypes);

        Set<Integer> userIds = new HashSet<>();
        for (CustomDataType customDataType: customDataTypes) {
            userIds.add(customDataType.getCreatorId());
        }
        userIds.add(getSUser().getId());

        Map<Integer,String> usersMap = UsersDao.instance.getUsernames(userIds);

        dataTypes = new BasicDBObject();
        dataTypes.put("customDataTypes", customDataTypes);
        dataTypes.put("usersMap", usersMap);
        List<SingleTypeInfo.SubType> subTypes = new ArrayList<>();
        for (SingleTypeInfo.SubType subType: SingleTypeInfo.subTypeMap.values()) {
            if (subType.isSensitiveAlways() || subType.getSensitivePosition().size() > 0) {
                subTypes.add(subType);
            }
        }
        dataTypes.put("aktoDataTypes", subTypes);

        return Action.SUCCESS.toUpperCase();
    }

    public BasicDBObject getDataTypes() {
        return dataTypes;
    }

    private CustomDataType customDataType;;

    @Override
    public String execute() {
        // TODO: handle errors
        if (name == null) return ERROR.toUpperCase();
        User user = getSUser();

        try {
            customDataType = generateCustomDataType(user.getId());
        } catch (AktoCustomException e) {
            return ERROR.toUpperCase();
        }

        if (this.createNew) {
            CustomDataType customDataTypeFromDb = CustomDataTypeDao.instance.findOne(Filters.eq(CustomDataType.NAME, name));
            if (customDataTypeFromDb != null) return ERROR.toUpperCase();
            // id is automatically set when inserting in pojo
            CustomDataTypeDao.instance.insertOne(customDataType);
        } else {
            FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
            options.returnDocument(ReturnDocument.AFTER);
            customDataType = CustomDataTypeDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq("name", customDataType.getName()),
                Updates.combine(
                    Updates.set(CustomDataType.SENSITIVE_ALWAYS,customDataType.isSensitiveAlways()),
                    Updates.set(CustomDataType.KEY_CONDITIONS,customDataType.getKeyConditions()),
                    Updates.set(CustomDataType.VALUE_CONDITIONS,customDataType.getValueConditions()),
                    Updates.set(CustomDataType.OPERATOR,customDataType.getOperator()),
                    Updates.set(CustomDataType.TIMESTAMP,Context.now())
                ),
                options
            );
        }


        return Action.SUCCESS.toUpperCase();
    }


    public static class CustomSubTypeMatch {

        private int apiCollectionId;
        private String url, method, key, value;

        public CustomSubTypeMatch(int apiCollectionId, String url, String method, String key, String value) {
            this.apiCollectionId = apiCollectionId;
            this.url = url;
            this.method = method;
            this.key = key;
            this.value = value;
        }

        public CustomSubTypeMatch() {
        }

        public int getApiCollectionId() {
            return apiCollectionId;
        }

        public void setApiCollectionId(int apiCollectionId) {
            this.apiCollectionId = apiCollectionId;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    private List<CustomSubTypeMatch> customSubTypeMatches;

    public static void main(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://172.18.0.2:27017/admini"));
        Context.accountId.set(1_000_000);

        CustomDataTypeAction customDataTypeAction = new CustomDataTypeAction();
        Map<String, Object> session = new HashMap<>();
        session.put("user", new User());
        customDataTypeAction.setSession(session);


        customDataTypeAction.setCreateNew(true);
        customDataTypeAction.setName("test_1");
        customDataTypeAction.setSensitiveAlways(true);
        customDataTypeAction.setOperator("AND");

        customDataTypeAction.setKeyOperator("AND");
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("value", "ship");
        customDataTypeAction.setKeyConditionFromUsers(
                Collections.singletonList(new ConditionFromUser(Predicate.Type.STARTS_WITH, valueMap))
        );

        customDataTypeAction.setValueOperator("AND");
        customDataTypeAction.setValueConditionFromUsers(Collections.emptyList());

        customDataTypeAction.reviewCustomDataType();

        System.out.println(customDataTypeAction.getCustomSubTypeMatches().get(0).getKey());
        System.out.println(customDataTypeAction.getCustomSubTypeMatches().get(0).getValue());
        System.out.println(customDataTypeAction.getCustomSubTypeMatches().get(1).getKey());
        System.out.println(customDataTypeAction.getCustomSubTypeMatches().get(1).getValue());
    }

    private int pageNum;
    public String reviewCustomDataType() {
        customSubTypeMatches = new ArrayList<>();
        CustomDataType customDataType;
        try {
            customDataType = generateCustomDataType(getSUser().getId());
        } catch (AktoCustomException e) {
            return ERROR.toUpperCase();
        }
        System.out.println("custom data type built");

        int pageSize = 10;
//        MongoCursor<SampleData> cursor = SampleDataDao.instance.getMCollection().find().skip(pageSize*(pageNum-1)).limit(pageSize).cursor();
        List<SampleData> sampleDataList = SampleDataDao.instance.findAll(new BasicDBObject());

//        while(cursor.hasNext()) {
//            SampleData elem = cursor.next();
//            sampleDataList.add(elem);
//        }

        System.out.println(sampleDataList.size());

        for (SampleData sampleData: sampleDataList) {
            List<String> samples = sampleData.getSamples();
            boolean skip = false;
            for (String sample: samples) {
                Key apiKey = sampleData.getId();
                try {
                    HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(sample);
                    skip = forHeaders(httpResponseParams.getHeaders(), customDataType, sample, apiKey);
                    skip = skip || forHeaders(httpResponseParams.requestParams.getHeaders(), customDataType, sample, apiKey);
                    skip = skip || forPayload(httpResponseParams.getPayload(), customDataType, sample, apiKey);
                    skip = skip || forPayload(httpResponseParams.requestParams.getPayload(), customDataType, sample, apiKey);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (skip) break;
            }
        }

        return SUCCESS.toUpperCase();
    }

    public boolean forHeaders(Map<String, List<String>> headers, CustomDataType customDataType, String sample, Key apiKey) {
        boolean matchFound = false;
        for (String headerName: headers.keySet()) {
            List<String> headerValues = headers.get(headerName);
            for (String value: headerValues) {
                boolean result = customDataType.validate(value,headerName);
                if (result) {
                    matchFound = true;
                    CustomSubTypeMatch customSubTypeMatch = new CustomSubTypeMatch(
                            apiKey.getApiCollectionId(),apiKey.url,apiKey.method.name(),headerName, value
                    );
                    this.customSubTypeMatches.add(customSubTypeMatch);
                }
            }
        }
        return matchFound;
    }

    public boolean forPayload(String payload, CustomDataType customDataType, String sample, Key apiKey) throws IOException {
        boolean matchFound = false;
        JsonParser jp = factory.createParser(payload);
        JsonNode node = mapper.readTree(jp);

        Map<String,Set<String>> responseParamMap = new HashMap<>();
        RelationshipSync.extractAllValuesFromPayload(node,new ArrayList<>(), responseParamMap);

        for (String param: responseParamMap.keySet()) {
            Iterator<String> iterator = responseParamMap.get(param).iterator();
            String key = param.replaceAll("#", ".").replaceAll("\\.\\$", "");
            while (iterator.hasNext()) {
                String value = iterator.next();
                boolean result = customDataType.validate(value, key);
                if (result) {
                    matchFound = true;
                    CustomSubTypeMatch customSubTypeMatch = new CustomSubTypeMatch(
                            apiKey.getApiCollectionId(),apiKey.url,apiKey.method.name(),key, value
                    );
                    this.customSubTypeMatches.add(customSubTypeMatch);
                }
            }
        }
        return matchFound;
    }

    public CustomDataType generateCustomDataType(int userId) throws AktoCustomException {
        Conditions keyConditions = null;
        if (keyConditionFromUsers != null && keyOperator != null) {

            Conditions.Operator kOperator;
            try {
                kOperator = Conditions.Operator.valueOf(keyOperator);
            } catch (Exception ignored) {
                throw new AktoCustomException("Invalid key operator");
            }

            List<Predicate> predicates = new ArrayList<>();
            for (ConditionFromUser conditionFromUser: keyConditionFromUsers) {
                Predicate predicate = generatePredicate(conditionFromUser.type, conditionFromUser.valueMap);
                if (predicate == null) {
                    throw new AktoCustomException("Invalid key conditions");
                } else {
                    predicates.add(predicate);
                }
            }

            if (predicates.size() > 0) {
                keyConditions = new Conditions(predicates, kOperator);
            }
        }

        Conditions valueConditions  = null;
        if (valueConditionFromUsers != null && valueOperator != null) {
            Conditions.Operator vOperator;
            try {
                vOperator = Conditions.Operator.valueOf(valueOperator);
            } catch (Exception ignored) {
                throw new AktoCustomException("Invalid value operator");
            }

            List<Predicate> predicates = new ArrayList<>();
            for (ConditionFromUser conditionFromUser: valueConditionFromUsers) {
                Predicate predicate = generatePredicate(conditionFromUser.type, conditionFromUser.valueMap);
                if (predicate == null) {
                    throw new AktoCustomException("Invalid value conditions");
                } else {
                    predicates.add(predicate);
                }
            }

            if (predicates.size() > 0) {
                valueConditions = new Conditions(predicates, vOperator);
            }
        }

        Conditions.Operator mainOperator;
        try {
            mainOperator = Conditions.Operator.valueOf(operator);
        } catch (Exception ignored) {
            throw new AktoCustomException("Invalid value operator");
        }

        return new CustomDataType(name, sensitiveAlways, Collections.emptyList(), userId,
                true,keyConditions,valueConditions, mainOperator);
    }

    private Predicate generatePredicate(Predicate.Type type, Map<String,Object> valueMap) {
        Predicate predicate;
        switch (type) {
            case REGEX:
                Object regex = valueMap.get("value");
                if (regex == null) return null;
                predicate = new RegexPredicate(regex.toString());
                return predicate;
            case STARTS_WITH:
                Object prefix = valueMap.get("value");
                if (prefix == null) return null;
                predicate = new StartsWithPredicate(prefix.toString());
                return predicate;
            case ENDS_WITH:
                Object suffix = valueMap.get("value");
                if (suffix == null) return null;
                predicate = new EndsWithPredicate(suffix.toString());
                return predicate;
            default:
                return null;
        }

    }


    public void setCreateNew(boolean createNew) {
        this.createNew = createNew;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSensitiveAlways(boolean sensitiveAlways) {
        this.sensitiveAlways = sensitiveAlways;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public void setKeyOperator(String keyOperator) {
        this.keyOperator = keyOperator;
    }

    public void setKeyConditionFromUsers(List<ConditionFromUser> keyConditionFromUsers) {
        this.keyConditionFromUsers = keyConditionFromUsers;
    }

    public void setValueOperator(String valueOperator) {
        this.valueOperator = valueOperator;
    }

    public void setValueConditionFromUsers(List<ConditionFromUser> valueConditionFromUsers) {
        this.valueConditionFromUsers = valueConditionFromUsers;
    }

    public List<CustomSubTypeMatch> getCustomSubTypeMatches() {
        return customSubTypeMatches;
    }

    public CustomDataType getCustomDataType() {
        return customDataType;
    }
}
