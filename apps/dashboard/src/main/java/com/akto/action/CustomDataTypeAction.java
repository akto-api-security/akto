package com.akto.action;


import com.akto.dao.CustomDataTypeDao;
import com.akto.dao.SampleDataDao;
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
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;

import java.io.IOException;
import java.util.*;

public class CustomDataTypeAction extends UserAction{

    private String name;
    private boolean sensitiveAlways;
    private String operator;

    private String keyOperator;
    private List<ConditionFromUser> keyConditionFromUsers;

    private String valueOperator;
    private List<ConditionFromUser> valueConditionFromUsers;

    public static class ConditionFromUser {
        PredicateType type;
        Map<String, Object> valueMap;

        public ConditionFromUser(PredicateType type, Map<String, Object> valueMap) {
            this.type = type;
            this.valueMap = valueMap;
        }

        public ConditionFromUser() { }

        public PredicateType getType() {
            return type;
        }

        public void setType(PredicateType type) {
            this.type = type;
        }

        public Map<String, Object> getValueMap() {
            return valueMap;
        }

        public void setValueMap(Map<String, Object> valueMap) {
            this.valueMap = valueMap;
        }
    }

    enum PredicateType {
        REGEX, STARTS_WITH, ENDS_WITH
    }

    private ObjectMapper mapper = new ObjectMapper();
    private JsonFactory factory = mapper.getFactory();

    @Override
    public String execute() {
        // TODO: handle errors

        if (name == null) {
            return ERROR.toUpperCase();
        }

        User user = getSUser();

        CustomDataType customDataType = null;
        try {
            customDataType = generateCustomDataType(user.getId());
        } catch (AktoCustomException e) {
            return ERROR.toUpperCase();
        }

        CustomDataType customDataTypeFromDb = CustomDataTypeDao.instance.findOne(Filters.eq(CustomDataType.NAME, name));
        if (customDataTypeFromDb != null) {
            return ERROR.toUpperCase();
        }

        CustomDataTypeDao.instance.insertOne(customDataType);

        return Action.SUCCESS.toUpperCase();
    }


    private Map<String, List<SingleTypeInfo.ParamId>> customSubTypeMatch = new HashMap<>();

    private int pageNum;
    public String getMatchesInSampleValues() {
        CustomDataType customDataType;
        try {
            customDataType = generateCustomDataType(getSUser().getId());
        } catch (AktoCustomException e) {
            return ERROR.toUpperCase();
        }

        int pageSize = 10;
        MongoCursor<SampleData> cursor = SampleDataDao.instance.getMCollection().find().skip(pageSize*(pageNum-1)).limit(pageSize).cursor();
        List<SampleData> sampleDataList = new ArrayList<>();

        while(cursor.hasNext()) {
            SampleData elem = cursor.next();
            sampleDataList.add(elem);
        }

        for (SampleData sampleData: sampleDataList) {
            List<String> samples = sampleData.getSamples();
            for (String sample: samples) {
                Key apiKey = sampleData.getId();
                try {
                    HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(sample);
                    forHeaders(httpResponseParams.getHeaders(), customDataType, sample, apiKey);
                    forHeaders(httpResponseParams.requestParams.getHeaders(), customDataType, sample, apiKey);
                    forPayload(httpResponseParams.getPayload(), customDataType, sample, apiKey);
                    forPayload(httpResponseParams.requestParams.getPayload(), customDataType, sample, apiKey);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return SUCCESS.toUpperCase();
    }

    public void forHeaders(Map<String, List<String>> headers, CustomDataType customDataType, String sample, Key apiKey) {
        for (String headerName: headers.keySet()) {
            List<String> headerValues = headers.get(headerName);
            for (String value: headerValues) {
                boolean result = customDataType.validate(value,headerName);
                if (result) {
                    List<SingleTypeInfo.ParamId> paramIdList = customSubTypeMatch.get(sample);
                    if (paramIdList == null) {
                        paramIdList = new ArrayList<>();
                        customSubTypeMatch.put(sample, paramIdList);
                    }
                    paramIdList.add(new SingleTypeInfo.ParamId(
                            apiKey.url,apiKey.method.name(),apiKey.responseCode,true,headerName, customDataType.toSubType(),apiKey.getApiCollectionId()
                    ));
                }
            }

        }
    }

    public void forPayload(String payload, CustomDataType customDataType, String sample, Key apiKey) throws IOException {

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
                    List<SingleTypeInfo.ParamId> paramIdList = customSubTypeMatch.get(sample);
                    if (paramIdList == null) {
                        paramIdList = new ArrayList<>();
                        customSubTypeMatch.put(sample, paramIdList);
                    }
                    paramIdList.add(new SingleTypeInfo.ParamId(
                            apiKey.url,apiKey.method.name(),apiKey.responseCode,false, param, customDataType.toSubType(),apiKey.getApiCollectionId()
                    ));
                }
            }
        }
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

    private Predicate generatePredicate(PredicateType type, Map<String,Object> valueMap) {
        Predicate predicate;
        switch (type) {
            case REGEX:
                Object regex = valueMap.get("regex");
                if (regex == null) return null;
                predicate = new RegexPredicate(regex.toString());
                return predicate;
            case STARTS_WITH:
                Object prefix = valueMap.get("prefix");
                if (prefix == null) return null;
                predicate = new StartsWithPredicate(prefix.toString());
                return predicate;
            case ENDS_WITH:
                Object suffix = valueMap.get("suffix");
                if (suffix == null) return null;
                predicate = new EndsWithPredicate(suffix.toString());
                return predicate;
            default:
                return null;
        }

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

    public Map<String, List<SingleTypeInfo.ParamId>> getCustomSubTypeMatch() {
        return customSubTypeMatch;
    }
}
