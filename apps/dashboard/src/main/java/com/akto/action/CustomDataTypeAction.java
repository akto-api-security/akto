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
import com.opensymphony.xwork2.Action;


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
        User user = getSUser();
        customDataType = null;
        try {
            customDataType = generateCustomDataType(user.getId());
        } catch (AktoCustomException e) {
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }

        if (this.createNew) {
            CustomDataType customDataTypeFromDb = CustomDataTypeDao.instance.findOne(Filters.eq(CustomDataType.NAME, name));
            if (customDataTypeFromDb != null) {
                addActionError("Data type with same name exists");
                return ERROR.toUpperCase();
            }
            // id is automatically set when inserting in pojo
            CustomDataTypeDao.instance.insertOne(customDataType);
        } else {
            FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
            options.returnDocument(ReturnDocument.AFTER);
            options.upsert(false);
            customDataType = CustomDataTypeDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq("name", customDataType.getName()),
                Updates.combine(
                    Updates.set(CustomDataType.SENSITIVE_ALWAYS,customDataType.isSensitiveAlways()),
                    Updates.set(CustomDataType.KEY_CONDITIONS,customDataType.getKeyConditions()),
                    Updates.set(CustomDataType.VALUE_CONDITIONS,customDataType.getValueConditions()),
                    Updates.set(CustomDataType.OPERATOR,customDataType.getOperator()),
                    Updates.set(CustomDataType.TIMESTAMP,Context.now()),
                    Updates.set(CustomDataType.ACTIVE,active)
                ),
                options
            );

            if (customDataType == null) {
                addActionError("Failed to update data type");
                return ERROR.toUpperCase();
            }
        }

        SingleTypeInfo.fetchCustomDataTypes();


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


    private int pageNum;
    private long totalSampleDataCount;
    private long currentProcessed;
    private static final int pageSize = 1000;
    public String reviewCustomDataType() {
        customSubTypeMatches = new ArrayList<>();
        CustomDataType customDataType;
        try {
            customDataType = generateCustomDataType(getSUser().getId());
        } catch (AktoCustomException e) {
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }

        totalSampleDataCount = SampleDataDao.instance.getMCollection().estimatedDocumentCount();

        MongoCursor<SampleData> cursor = SampleDataDao.instance.getMCollection().find().skip(pageSize*(pageNum-1)).limit(pageSize).cursor();
        currentProcessed = 0;
        while(cursor.hasNext()) {
            SampleData sampleData = cursor.next();

            List<String> samples = sampleData.getSamples();
            boolean skip = false;
            for (String sample: samples) {
                Key apiKey = sampleData.getId();
                try {
                    HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(sample);
                    boolean skip1 = forHeaders(httpResponseParams.getHeaders(), customDataType, apiKey);
                    boolean skip2 = forHeaders(httpResponseParams.requestParams.getHeaders(), customDataType, apiKey);
                    boolean skip3 = forPayload(httpResponseParams.getPayload(), customDataType, apiKey);
                    boolean skip4 = forPayload(httpResponseParams.requestParams.getPayload(), customDataType, apiKey);
                    skip = skip1 || skip2 || skip3 || skip4;
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (skip) break;
            }

            currentProcessed += 1;
        }

        return SUCCESS.toUpperCase();
    }

    public boolean forHeaders(Map<String, List<String>> headers, CustomDataType customDataType, Key apiKey) {
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

    public boolean forPayload(String payload, CustomDataType customDataType, Key apiKey) throws IOException {
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
        // TODO: handle errors
        if (name == null || name.length() == 0) throw new AktoCustomException("Name cannot be empty");
        if (name.split(" ").length > 1) throw new AktoCustomException("Name has to be single word");
        name = name.trim();
        name = name.toUpperCase();
        if (!(name.matches("[A-Z_0-9]+"))) throw new AktoCustomException("Name can only contain alphabets, numbers and underscores");

        if (SingleTypeInfo.subTypeMap.containsKey(name)) {
            throw new AktoCustomException("Data type name reserved");
        }


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

        if ((keyConditions == null || keyConditions.getPredicates() == null || keyConditions.getPredicates().size() == 0) &&
              (valueConditions == null || valueConditions.getPredicates() ==null || valueConditions.getPredicates().size() == 0))  {

            throw new AktoCustomException("Both key and value conditions can't be empty");
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

    static Predicate generatePredicate(Predicate.Type type, Map<String,Object> valueMap) {
        if (valueMap == null || type == null) return null;
        Predicate predicate;
        String value;
        switch (type) {
            case REGEX:
                Object regex = valueMap.get("value");
                if (!(regex instanceof String)) return null;
                value = regex.toString();
                if (value.length() == 0) return null;
                predicate = new RegexPredicate(value);
                return predicate;
            case STARTS_WITH:
                Object prefix = valueMap.get("value");
                if (!(prefix instanceof String)) return null;
                value = prefix.toString();
                if (value.length() == 0) return null;
                predicate = new StartsWithPredicate(value);
                return predicate;
            case ENDS_WITH:
                Object suffix = valueMap.get("value");
                if (!(suffix instanceof String)) return null;
                value = suffix.toString();
                if (value.length() == 0) return null;
                predicate = new EndsWithPredicate(value);
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

    private boolean active;
    public String toggleDataTypeActiveParam() {
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.returnDocument(ReturnDocument.AFTER);
        options.upsert(false);
        customDataType = CustomDataTypeDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq(CustomDataType.NAME, this.name),
                Updates.set(CustomDataType.ACTIVE, active),
                options
        );

        if (customDataType == null) {
            String v = active ? "activate" : "deactivate";
            addActionError("Failed to "+ v +" data type");
            return ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public long getTotalSampleDataCount() {
        return totalSampleDataCount;
    }

    public long getCurrentProcessed() {
        return currentProcessed;
    }
}
