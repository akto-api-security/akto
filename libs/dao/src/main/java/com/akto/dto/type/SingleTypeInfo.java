package com.akto.dto.type;

import com.akto.dao.AktoDataTypeDao;
import com.akto.dao.CustomAuthTypeDao;
import com.akto.dao.CustomDataTypeDao;
import com.akto.dao.context.Context;
import com.akto.dto.AktoDataType;
import com.akto.dto.CustomAuthType;
import com.akto.dto.CustomDataType;
import com.akto.types.CappedSet;
import com.akto.util.AccountTask;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import io.swagger.v3.oas.models.media.*;

import org.apache.commons.lang3.StringUtils;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.primitives.Longs.min;
import static java.lang.Long.max;

public class SingleTypeInfo {

    private static final Logger logger = LoggerFactory.getLogger(SingleTypeInfo.class);
    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final Map<Integer, AccountDataTypesInfo> accountToDataTypesInfo = new HashMap<>();
    public static final Map<Integer, List<CustomAuthType>> activeCustomAuthTypes = new HashMap<>();

    public static Map<String, CustomDataType> getCustomDataTypeMap(int accountId) {
        if (accountToDataTypesInfo.containsKey(accountId)) {
            return accountToDataTypesInfo.get(accountId).getCustomDataTypeMap();
        } else {
            return new HashMap<>();
        }
    }

        public static boolean isCustomDataTypeAvailable(int accountId){
        if(accountToDataTypesInfo.containsKey(accountId)){
            return !accountToDataTypesInfo.get(accountId).getRedactedDataTypes().isEmpty();
        }
        return false;
    }

    public static List<CustomAuthType> getCustomAuthType (int accountId) {
        List<CustomAuthType> customAuthTypes = SingleTypeInfo.activeCustomAuthTypes.get(accountId);
        if (customAuthTypes == null) {
            customAuthTypes = new ArrayList<>();
        }
        return customAuthTypes;
    }

    public static Map<String, AktoDataType> getAktoDataTypeMap(int accountId) {
        if (accountToDataTypesInfo.containsKey(accountId)) {
            return accountToDataTypesInfo.get(accountId).getAktoDataTypeMap();
        } else {
            return new HashMap<>();
        }
    }

    public static List<CustomDataType> getCustomDataTypesSortedBySensitivity(int accountId) {
        if (accountToDataTypesInfo.containsKey(accountId)) {
            return accountToDataTypesInfo.get(accountId).getCustomDataTypesSortedBySensitivity();
        } else {
            return new ArrayList<>();
        }
    }

    public static Map<Integer, AccountDataTypesInfo> getAccountToDataTypesInfo () {
        return accountToDataTypesInfo;
    }
    public static void init() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(t -> {
                    fetchCustomDataTypes(t.getId());
                    fetchCustomAuthTypes(t.getId());
                }, "populate-data-types-info");
            }
        }, 0, 5, TimeUnit.MINUTES);

    }

    public static void initFromRuntime(List<CustomDataType> customDataTypes, List<AktoDataType> aktoDataTypes,
                                       List<CustomAuthType> customAuthTypes, int accountId) {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                fetchCustomDataTypes(accountId, customDataTypes, aktoDataTypes);
                fetchCustomAuthTypes(accountId, customAuthTypes);
            }
        }, 0, 5, TimeUnit.MINUTES);

    }

    public static String findLastKeyFromParam(String param) {
        if (param == null) return null;
        String paramReplaced = param.replaceAll("#", ".").replaceAll("\\.\\$", "");
        String[] paramList = paramReplaced.split("\\.");
        return paramList[paramList.length-1]; // choosing the last key
    }

    public static void fetchCustomDataTypes(int accountId) {
        List<CustomDataType> customDataTypes = CustomDataTypeDao.instance.findAll(new BasicDBObject());
        Map<String, CustomDataType> newMap = new HashMap<>();
        List<CustomDataType> sensitiveCustomDataType = new ArrayList<>();
        List<CustomDataType> nonSensitiveCustomDataType = new ArrayList<>();
        Set<String> redactedDataTypes = new HashSet<>();
        for (CustomDataType customDataType: customDataTypes) {
            newMap.put(customDataType.getName(), customDataType);
            if (customDataType.isSensitiveAlways() || customDataType.getSensitivePosition().size()>0) {
                sensitiveCustomDataType.add(customDataType);
            } else {
                nonSensitiveCustomDataType.add(customDataType);
            }
            if (customDataType.isRedacted()) {
                redactedDataTypes.add(customDataType.getName());
            }
        }

        accountToDataTypesInfo.putIfAbsent(accountId, new AccountDataTypesInfo());

        AccountDataTypesInfo info = accountToDataTypesInfo.get(accountId);

        info.setCustomDataTypeMap(newMap);
        sensitiveCustomDataType.addAll(nonSensitiveCustomDataType);
        info.setCustomDataTypesSortedBySensitivity(new ArrayList<>(sensitiveCustomDataType));
        List<AktoDataType> aktoDataTypes = AktoDataTypeDao.instance.findAll(new BasicDBObject());
        Map<String,AktoDataType> newAktoMap = new HashMap<>();
        for(AktoDataType aktoDataType:aktoDataTypes){
            if(subTypeMap.containsKey(aktoDataType.getName())){
                newAktoMap.put(aktoDataType.getName(), aktoDataType);
                subTypeMap.get(aktoDataType.getName()).setSensitiveAlways(aktoDataType.getSensitiveAlways());
                subTypeMap.get(aktoDataType.getName()).setSensitivePosition(aktoDataType.getSensitivePosition());
                if(aktoDataType.isRedacted()){
                    redactedDataTypes.add(aktoDataType.getName());
                }
            }
        }
        info.setAktoDataTypeMap(newAktoMap);
        info.setRedactedDataTypes(redactedDataTypes);
    }

    public static Bson getFilterFromParamId(ParamId paramId) {
        return Filters.and(
            Filters.eq(SingleTypeInfo._URL, paramId.getUrl()),
            Filters.eq(SingleTypeInfo._METHOD, paramId.getMethod()),
            Filters.eq(SingleTypeInfo._RESPONSE_CODE, paramId.getResponseCode()),
            Filters.eq(SingleTypeInfo._IS_HEADER, paramId.isHeader()),
            Filters.eq(SingleTypeInfo._PARAM, paramId.getParam()),
            Filters.eq(SingleTypeInfo.SUB_TYPE, paramId.getSubType().name),
            Filters.eq(SingleTypeInfo._API_COLLECTION_ID, paramId.getApiCollectionId())
        );
    }

    public static void fetchCustomDataTypes(int accountId, List<CustomDataType> customDataTypes,
                                            List<AktoDataType> aktoDataTypes) {
        Map<String, CustomDataType> newMap = new HashMap<>();
        List<CustomDataType> sensitiveCustomDataType = new ArrayList<>();
        List<CustomDataType> nonSensitiveCustomDataType = new ArrayList<>();
        Set<String> redactedDataTypes = new HashSet<>();
        for (CustomDataType customDataType: customDataTypes) {
            newMap.put(customDataType.getName(), customDataType);
            if (customDataType.isSensitiveAlways() || customDataType.getSensitivePosition().size()>0) {
                sensitiveCustomDataType.add(customDataType);
            } else {
                nonSensitiveCustomDataType.add(customDataType);
            }
            if (customDataType.isRedacted()) {
                redactedDataTypes.add(customDataType.getName());
            }
        }

        accountToDataTypesInfo.putIfAbsent(accountId, new AccountDataTypesInfo());

        AccountDataTypesInfo info = accountToDataTypesInfo.get(accountId);

        info.setCustomDataTypeMap(newMap);
        sensitiveCustomDataType.addAll(nonSensitiveCustomDataType);
        info.setCustomDataTypesSortedBySensitivity(new ArrayList<>(sensitiveCustomDataType));
        Map<String,AktoDataType> newAktoMap = new HashMap<>();
        for(AktoDataType aktoDataType:aktoDataTypes){
            if(subTypeMap.containsKey(aktoDataType.getName())){
                newAktoMap.put(aktoDataType.getName(), aktoDataType);
                subTypeMap.get(aktoDataType.getName()).setSensitiveAlways(aktoDataType.getSensitiveAlways());
                subTypeMap.get(aktoDataType.getName()).setSensitivePosition(aktoDataType.getSensitivePosition());
            }
        }
        info.setAktoDataTypeMap(newAktoMap);
        info.setRedactedDataTypes(redactedDataTypes);
    }

    public static boolean isRedacted(String dataTypeName){
        if (accountToDataTypesInfo.containsKey(Context.accountId.get())) {
            return accountToDataTypesInfo.get(Context.accountId.get()).getRedactedDataTypes().contains(dataTypeName);
        } else {
            return false;
        }
    }

    public static void fetchCustomAuthTypes(int accountId) {
        activeCustomAuthTypes.put(accountId, CustomAuthTypeDao.instance.findAll(CustomAuthType.ACTIVE,true));
    }

    public static void fetchCustomAuthTypes(int accountId, List<CustomAuthType> customAuthTypes) {
        activeCustomAuthTypes.put(accountId, customAuthTypes);
    }

    public enum SuperType {
        BOOLEAN, INTEGER, FLOAT, STRING, OBJECT_ID, NULL, OTHER, CUSTOM, VERSIONED
    }

    public enum Position {
        REQUEST_HEADER, REQUEST_PAYLOAD, RESPONSE_HEADER, RESPONSE_PAYLOAD
    }

    public static final SubType TRUE = new SubType("TRUE", false, SuperType.BOOLEAN, BooleanSchema.class,
            Collections.emptyList());
    public static final SubType FALSE = new SubType("FALSE", false, SuperType.BOOLEAN, BooleanSchema.class,
            Collections.emptyList());
    public static final SubType INTEGER_32 = new SubType("INTEGER_32", false, SuperType.INTEGER, IntegerSchema.class,
            Collections.emptyList());
    public static final SubType INTEGER_64 = new SubType("INTEGER_64", false, SuperType.INTEGER, IntegerSchema.class,
            Collections.emptyList());
    public static final SubType FLOAT = new SubType("FLOAT", false, SuperType.FLOAT, NumberSchema.class,
            Collections.emptyList());
    public static final SubType NULL = new SubType("NULL", false, SuperType.STRING, StringSchema.class,
            Collections.emptyList());
    public static final SubType OTHER = new SubType("OTHER", false, SuperType.STRING, StringSchema.class,
            Collections.emptyList());
    public static SubType EMAIL = new SubType("EMAIL", true, SuperType.STRING, EmailSchema.class,
            Collections.emptyList());
    public static final SubType URL = new SubType("URL", false, SuperType.STRING, StringSchema.class,
            Collections.emptyList());
    public static SubType ADDRESS = new SubType("ADDRESS", true, SuperType.STRING, StringSchema.class,
            Collections.emptyList());
    public static SubType SSN = new SubType("SSN", true, SuperType.STRING, StringSchema.class,
            Collections.emptyList());
    public static SubType CREDIT_CARD = new SubType("CREDIT_CARD", true, SuperType.STRING, StringSchema.class,
            Collections.emptyList());
    public static SubType VIN = new SubType("VIN", true, SuperType.STRING, StringSchema.class,
            Collections.emptyList());
    public static SubType PHONE_NUMBER = new SubType("PHONE_NUMBER", true, SuperType.STRING, StringSchema.class,
            Collections.emptyList());
    public static SubType UUID = new SubType("UUID", false, SuperType.STRING, StringSchema.class,
            Collections.emptyList());
    public static final SubType GENERIC = new SubType("GENERIC", false, SuperType.STRING, StringSchema.class,
            Collections.emptyList());
    public static final SubType DICT = new SubType("DICT", false, SuperType.STRING, MapSchema.class,
            Collections.emptyList());
    public static SubType JWT = new SubType("JWT", false, SuperType.STRING, StringSchema.class,
            Arrays.asList(Position.RESPONSE_PAYLOAD, Position.RESPONSE_HEADER));
    public static SubType IP_ADDRESS = new SubType("IP_ADDRESS", false, SuperType.STRING, StringSchema.class,
            Arrays.asList(Position.RESPONSE_PAYLOAD, Position.RESPONSE_HEADER));
    // make sure to add AKTO subTypes to subTypeMap below

    public static boolean doesNotStartWithSuperType(String input) {
        if (input == null) return true;

        return Arrays.stream(SuperType.values())
                .map(Enum::name)
                .noneMatch(input::startsWith);
    }

    public static class SubType {
        private String name;
        private boolean sensitiveAlways;
        private SuperType superType;
        private Class<? extends Schema> swaggerSchemaClass;
        private List<Position> sensitivePosition;

        public SubType() {
        }

        public SubType(String name, boolean sensitiveAlways, SuperType superType,
                Class<? extends Schema> swaggerSchemaClass, List<Position> sensitivePosition) {
            this.name = name;
            this.sensitiveAlways = sensitiveAlways;
            this.superType = superType;
            this.swaggerSchemaClass = swaggerSchemaClass;
            this.sensitivePosition = sensitivePosition;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SubType subType = (SubType) o;
            return sensitiveAlways == subType.sensitiveAlways && name.equals(subType.name) && superType == subType.superType && swaggerSchemaClass.equals(subType.swaggerSchemaClass) && sensitivePosition.equals(subType.sensitivePosition);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, sensitiveAlways, superType, swaggerSchemaClass, sensitivePosition);
        }

        @Override
        public String toString() {
            return "SubType{" +
                    "name='" + name + '\'' +
                    ", sensitiveAlways=" + sensitiveAlways +
                    ", superType=" + superType +
                    ", swaggerSchemaClass=" + swaggerSchemaClass +
                    ", sensitivePosition=" + sensitivePosition +
                    '}';
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isSensitiveAlways() {
            return sensitiveAlways;
        }

        public void setSensitiveAlways(boolean sensitiveAlways) {
            this.sensitiveAlways = sensitiveAlways;
        }

        public SuperType getSuperType() {
            return superType;
        }

        public void setSuperType(SuperType superType) {
            this.superType = superType;
        }

        public Class<? extends Schema> getSwaggerSchemaClass() {
            return swaggerSchemaClass;
        }

        public void setSwaggerSchemaClass(Class<? extends Schema> swaggerSchemaClass) {
            this.swaggerSchemaClass = swaggerSchemaClass;
        }

        public List<Position> getSensitivePosition() {
            return sensitivePosition;
        }

        public void setSensitivePosition(List<Position> sensitivePosition) {
            this.sensitivePosition = sensitivePosition;
        }

        // Calculates and tells if sensitive or not based on sensitiveAlways and sensitivePosition fields
        public boolean isSensitive(Position position) {
            if (this.sensitiveAlways) return true;
            return this.sensitivePosition.contains(position);
        }
    }

    public Position findPosition() {
        return findPosition(responseCode, isHeader);
    }

    public static Position findPosition(int responseCode, boolean isHeader) {
        SingleTypeInfo.Position position;
        if (responseCode == -1) {
            if (isHeader) {
                position = SingleTypeInfo.Position.REQUEST_HEADER;
            } else {
                position = SingleTypeInfo.Position.REQUEST_PAYLOAD;
            }
        } else {
            if (isHeader) {
                position = SingleTypeInfo.Position.RESPONSE_HEADER;
            } else {
                position = SingleTypeInfo.Position.RESPONSE_PAYLOAD;
            }
        }

        return position;
    }

    public static class ParamId {
        String url;
        String method;
        int responseCode;
        boolean isHeader;
        String param;
        @BsonIgnore
        SubType subType;
        int apiCollectionId;
        @BsonProperty("subType")
        String subTypeString;
        boolean isUrlParam;
        public ParamId(String url, String method, int responseCode, boolean isHeader, String param, SubType subType,
                       int apiCollectionId, boolean isUrlParam) {
            this.url = url;
            this.method = method;
            this.responseCode = responseCode;
            this.isHeader = isHeader;
            this.param = param;
            this.subType = subType;
            this.apiCollectionId = apiCollectionId;
            this.isUrlParam = isUrlParam;
        }

        public ParamId() {
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

        public int getResponseCode() {
            return responseCode;
        }

        public void setResponseCode(int responseCode) {
            this.responseCode = responseCode;
        }

        public boolean isHeader() {
            return isHeader;
        }

        public boolean getIsHeader() {
            return isHeader;
        }

        public void setIsHeader(boolean header) {
            isHeader = header;
        }

        public String getParam() {
            return param;
        }

        public void setParam(String param) {
            this.param = param;
        }

        public SubType getSubType() {
            return subType;
        }

        public void setSubType(SubType subType) {
            this.subType = subType;
        }        

        public String getSubTypeString() {
            if (subType == null) return null;
            return subType.name;
        }

        public void setSubTypeString(String subTypeString) {
            this.subTypeString = subTypeString;
            this.subType = subTypeMap.get(subTypeString);
            if (this.subType == null) {
                CustomDataType customDataType = getCustomDataTypeMap(Context.accountId.get()).get(subTypeString);
                if (customDataType != null) {
                    this.subType = customDataType.toSubType();
                } else {
                    // TODO:
                    this.subType = GENERIC;
                }
            }
        }

        public int getApiCollectionId() {
            return apiCollectionId;
        }

        public void setApiCollectionId(int apiCollectionId) {
            this.apiCollectionId = apiCollectionId;
        }

        public boolean getIsUrlParam() {
            return isUrlParam;
        }

        public void setIsUrlParam(boolean urlParam) {
            isUrlParam = urlParam;
        }

        @Override
        public String toString() {
            return "ParamId{" +
                    "url='" + url + '\'' +
                    ", method='" + method + '\'' +
                    ", responseCode=" + responseCode +
                    ", isHeader=" + isHeader +
                    ", param='" + param + '\'' +
                    ", subType=" + subType +
                    ", apiCollectionId=" + apiCollectionId +
                    ", subTypeString='" + subTypeString + '\'' +
                    ", isUrlParam=" + isUrlParam +
                    '}';
        }
    }

    ObjectId id;
    public static final String _URL = "url";
    @BsonIgnore
    String strId;
    String url;
    public static final String _METHOD = "method";
    String method;
    public static final String _RESPONSE_CODE = "responseCode";
    int responseCode;
    public static final String _IS_HEADER = "isHeader";
    boolean isHeader;
    public static final String _PARAM = "param";
    String param;
    public static final String SUB_TYPE = "subType";
    @BsonIgnore
    SubType subType;
    public static final String SUBTYPE_STRING = "subTypeString";
    @BsonProperty("subType")
    String subTypeString;
    public static final String _EXAMPLES  = "examples";
    @BsonIgnore
    Set<Object> examples = new HashSet<>();
    public static final String _USER_IDS = "userIds";
    @BsonIgnore
    Set<String> userIds = new HashSet<>();
    public static final String _COUNT = "count";
    int count;
    public static final String _TIMESTAMP = "timestamp";
    int timestamp;
    public static final String _DURATION = "duration";
    int duration;
    public static final String _API_COLLECTION_ID = "apiCollectionId";
    public static final String COLLECTION_NAME = "collectionName";
    int apiCollectionId;
    public static final String _COLLECTION_IDS = "collectionIds";
    List<Integer> collectionIds;
    public static final String _SENSITIVE = "sensitive";
    @BsonIgnore
    boolean sensitive;
    public static final String _IS_URL_PARAM = "isUrlParam";
    boolean isUrlParam;
    public static final String _VALUES = "values";
    public static final int VALUES_LIMIT = 50;
    CappedSet<String> values = new CappedSet<>();
    public static final String _DOMAIN = "domain";
    Domain domain = Domain.ENUM;
    public static final String MIN_VALUE = "minValue";
    public static final long ACCEPTED_MAX_VALUE =  Long.MAX_VALUE - 100_000;
    long minValue = ACCEPTED_MAX_VALUE; // this value will be used when field doesn't exist in db
    public static final String MAX_VALUE = "maxValue";
    public static final long ACCEPTED_MIN_VALUE =  Long.MIN_VALUE + 100_000;
    long maxValue = ACCEPTED_MIN_VALUE;  // this value will be used when field doesn't exist in db
    public static final String LAST_SEEN = "lastSeen";
    long lastSeen;
    public static final String SOURCES = "sources";
    Map<String, Object> sources;

    @BsonIgnore
    private boolean isPrivate; // do not use this field anywhere else. This was added to convey if STI is private or not to frontend
    @BsonIgnore
    private Object value;
    private boolean isQueryParam;

    public boolean isQueryParam() {
        return isQueryParam;
    }

    public void setQueryParam(boolean isQueryParam) {
        this.isQueryParam = isQueryParam;
    }

    public static final String _UNIQUE_COUNT = "uniqueCount";
    public long uniqueCount = 0L;
    public static final String _PUBLIC_COUNT = "publicCount";
    public long publicCount = 0L;
    public static final double THRESHOLD = 0.1;

    public enum Domain {
        ENUM, RANGE, ANY
    }

    public static final Map<String, SubType> subTypeMap = new HashMap<>();
    static {
        subTypeMap.put("TRUE", TRUE);
        subTypeMap.put("FALSE", FALSE);
        subTypeMap.put("INTEGER_32", INTEGER_32);
        subTypeMap.put("INTEGER_64", INTEGER_64);
        subTypeMap.put("FLOAT", FLOAT);
        subTypeMap.put("NULL", NULL);
        subTypeMap.put("OTHER", OTHER);
        subTypeMap.put("EMAIL", EMAIL);
        subTypeMap.put("URL", URL);
        subTypeMap.put("ADDRESS", ADDRESS);
        subTypeMap.put("SSN", SSN);
        subTypeMap.put("CREDIT_CARD", CREDIT_CARD);
        subTypeMap.put("VIN", VIN);
        subTypeMap.put("PHONE_NUMBER", PHONE_NUMBER);
        subTypeMap.put("UUID", UUID);
        subTypeMap.put("GENERIC", GENERIC);
        subTypeMap.put("DICT", DICT);
        subTypeMap.put("JWT", JWT);
        subTypeMap.put("IP_ADDRESS", IP_ADDRESS);
    }

    public SingleTypeInfo() {
    }

    public SingleTypeInfo(ParamId paramId, Set<Object> examples, Set<String> userIds, int count, int timestamp,
                          int duration, CappedSet<String> values, Domain domain, long minValue, long maxValue) {
        this.url = paramId.url;
        this.method = paramId.method;
        this.responseCode = paramId.responseCode;
        this.isHeader = paramId.isHeader;
        this.param = paramId.param;
        this.subType = paramId.subType;
        this.apiCollectionId = paramId.apiCollectionId;
        this.collectionIds = Arrays.asList(paramId.apiCollectionId);
        this.isUrlParam = paramId.isUrlParam;
        this.examples = examples;
        this.userIds = userIds;
        this.count = count;
        this.timestamp = timestamp;
        this.duration = duration;
        this.lastSeen = Context.now();
        this.values = values;
        this.domain = domain;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public String composeKey() {
        return composeKey(url, method, responseCode, isHeader, param, subType, apiCollectionId, isUrlParam);
    }

public String composeKeyWithCustomSubType(SubType s) {
        return composeKey(url, method, responseCode, isHeader, param, s, apiCollectionId, isUrlParam);
    }

    public static String composeKey(String url, String method, int responseCode, boolean isHeader, String param, SubType subType, int apiCollectionId, boolean isUrlParam) {
        return StringUtils.joinWith("@", url, method, responseCode, isHeader, param, subType, apiCollectionId, isUrlParam);
    }


    public void incr() {
        this.count++;
    }
    
    public SingleTypeInfo copy() {
        Set<Object> copyExamples = new HashSet<>();
        copyExamples.addAll(this.examples);

        Set<String> copyUserIds = new HashSet<>();
        copyUserIds.addAll(this.userIds);

        CappedSet<String> copyValues = new CappedSet<>(new HashSet<>(this.values.getElements()));

        ParamId paramId = new ParamId();
        paramId.url = url;
        paramId.method = method;
        paramId.responseCode = responseCode;
        paramId.isHeader = isHeader;
        paramId.param = param;
        paramId.subType = new SubType(subType.name, subType.sensitiveAlways, subType.superType, subType.swaggerSchemaClass, subType.sensitivePosition);
        paramId.apiCollectionId = apiCollectionId;
        paramId.isUrlParam = isUrlParam;

        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, copyExamples, copyUserIds, this.count,
                this.timestamp, this.duration, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
        singleTypeInfo.setValues(copyValues);
        singleTypeInfo.minValue = this.minValue;
        singleTypeInfo.maxValue = this.maxValue;
        singleTypeInfo.domain = this.domain;
        singleTypeInfo.uniqueCount = this.uniqueCount;
        singleTypeInfo.publicCount = this.publicCount;
        return singleTypeInfo;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return this.method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public int getResponseCode() {
        return this.responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public boolean isIsHeader() {
        return this.isHeader;
    }

    public boolean getIsHeader() {
        return this.isHeader;
    }

    public void setIsHeader(boolean isHeader) {
        this.isHeader = isHeader;
    }

    public String getParam() {
        return this.param;
    }

    public void setParam(String param) {
        this.param = param;
    }

    public SubType getSubType() {
        return this.subType;
    }

    public void setSubType(SubType subType) {
        this.subType = subType;
    }

    public Set<Object> getExamples() {
        return this.examples;
    }

    public void setExamples(Set<Object> examples) {
        this.examples = examples;
    }

    public Set<String> getUserIds() {
        return this.userIds;
    }

    public void setUserIds(Set<String> userIds) {
        this.userIds = userIds;
    }

    public int getCount() {
        return this.count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public int getDuration() {
        return this.duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }
    
    public int getApiCollectionId() {
        return this.apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public List<Integer> getCollectionIds() {
        return collectionIds;
    }

    public void setCollectionIds(List<Integer> collectionIds) {
        this.collectionIds = collectionIds;
    }

    public String getSubTypeString() {
        if (subType == null) return null;
        return subType.name;
    }

    public String getStrId() {
        if (strId == null) {
            return this.id.toHexString();
        }
        return this.strId;
    }

    public void setStrId(String strId) {
        this.strId = strId;
    }

    public Map<String, Object> getSources() {
        return this.sources;
    }

    public void setSources(Map<String, Object> sources) {
        this.sources = sources;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof SingleTypeInfo)) {
            return false;
        }
        SingleTypeInfo singleTypeInfo = (SingleTypeInfo) o;
        return url.equals(singleTypeInfo.url) &&
                    method.equals(singleTypeInfo.method) &&
                    responseCode == singleTypeInfo.responseCode &&
                    isHeader == singleTypeInfo.isHeader &&
                    param.equals(singleTypeInfo.param) &&
                    subType.equals(singleTypeInfo.subType) &&
                    apiCollectionId == singleTypeInfo.apiCollectionId &&
                    isUrlParam == singleTypeInfo.isUrlParam;
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, method, responseCode, isHeader, param, subType, apiCollectionId, isUrlParam);
    }

    @Override
    public String toString() {
        return "{" +
            " url='" + getUrl() + "'" +
            ", method='" + getMethod() + "'" +
            ", responseCode='" + getResponseCode() + "'" +
            ", isHeader='" + isIsHeader() + "'" +
            ", param='" + getParam() + "'" +
            ", subType='" + getSubType().name + "'" +
            ", apiCollectionId='" + getApiCollectionId() + "'" +
            ", examples='" + getExamples() + "'" +
            ", userIds='" + getUserIds() + "'" +
            ", count='" + getCount() + "'" +
            ", timestamp='" + getTimestamp() + "'" +
            ", duration='" + getDuration() + "'" +
            "}";
    }

    public void setSubTypeString(String subTypeString) {
        this.subTypeString = subTypeString;
        this.subType = subTypeMap.get(subTypeString);
        if (this.subType == null) {
            CustomDataType customDataType = getCustomDataTypeMap(Context.accountId.get()).get(subTypeString);
            if (customDataType != null) {
                this.subType = customDataType.toSubType();
            } else {
                // TODO:
                this.subType = GENERIC;
            }
        }
    }

    public boolean getSensitive() {
        if (this.subType == null) return false; // this was done for paramStateAction because it uses projections and doesn't return subType
        return this.subType.isSensitive(this.findPosition());
    }


    public CappedSet<String> getValues() {
        return values;
    }

    public void setValues(CappedSet<String> values) {
        this.values = values;
    }

    public Domain getDomain() {
        return domain;
    }

    public void setDomain(Domain domain) {
        this.domain = domain;
    }

    public boolean getIsUrlParam() {
        return isUrlParam;
    }

    public void setIsUrlParam(boolean urlParam) {
        isUrlParam = urlParam;
    }

    public void updateMinMaxValues(Object o) {
        if (subType.getSuperType() == SingleTypeInfo.SuperType.INTEGER || subType.getSuperType() == SingleTypeInfo.SuperType.FLOAT) {
            try {
                // this is done so that both integer and decimal values can be parsed
                // But while storing double we omit the decimal part
                double d = Double.parseDouble(o.toString());
                long l = (long) d;
                this.minValue = min(this.minValue, l);
                this.minValue = max(this.minValue, ACCEPTED_MIN_VALUE);

                this.maxValue = max(this.maxValue, l);
                this.maxValue = min(this.maxValue, ACCEPTED_MAX_VALUE);
                if (this.maxValue > ACCEPTED_MAX_VALUE) {
                    this.maxValue = ACCEPTED_MAX_VALUE;
                }
            } catch (Exception e) {
                logger.error("ERROR: while parsing long for min max in sti " + o.toString());
            }
        }
    }

    public void incPublicCount(int c) {
        this.publicCount += c;
    }

    public void incUniqueCount(int c) {
        this.uniqueCount += c;
    }

    public void merge(SingleTypeInfo that) {
        if (that != null) {
            this.count += that.getCount();
            this.values.getElements().addAll(that.values.getElements());
            this.minValue = min(this.minValue, that.minValue);
            this.maxValue = max(this.maxValue, that.maxValue);
            this.lastSeen = max(this.lastSeen, that.lastSeen);
            this.publicCount += that.publicCount;
            this.uniqueCount += that.uniqueCount;
        }
    }

    public void clearValues() {
        this.values = new CappedSet<>();
    }

    public boolean getIsPrivate() {
        if (uniqueCount == 0) return true;
        double v = (1.0*publicCount) / uniqueCount;
        return v <= SingleTypeInfo.THRESHOLD;
    }

    public long getMinValue() {
        return minValue;
    }

    public void setMinValue(long minValue) {
        this.minValue = minValue;
    }

    public long getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(long maxValue) {
        this.maxValue = maxValue;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public long getUniqueCount() {
        return uniqueCount;
    }

    public void setUniqueCount(long uniqueCount) {
        this.uniqueCount = uniqueCount;
    }

    public long getPublicCount() {
        return publicCount;
    }

    public void setPublicCount(long publicCount) {
        this.publicCount = publicCount;
    }

    public String composeApiInfoKey() {
        if (this.getMethod() == null) {
            return null;
        }
        if (this.getUrl() == null) {
            return null;
        }
        return this.getApiCollectionId() + " " + this.getUrl() + " " + this.getMethod();
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public boolean getIsQueryParam() {
        return isQueryParam;
    }

    public void setIsQueryParam(boolean isQueryParam) {
        this.isQueryParam = isQueryParam;
    }
}
