package com.akto.dto.type;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.dao.CustomDataTypeDao;
import com.akto.dao.context.Context;
import com.akto.dto.CustomDataType;
import com.akto.types.CappedSet;
import com.mongodb.BasicDBObject;
import io.swagger.v3.oas.models.media.*;
import org.apache.commons.lang3.StringUtils;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.primitives.Longs.min;
import static java.lang.Long.max;

public class SingleTypeInfo {

    private static final Logger logger = LoggerFactory.getLogger(SingleTypeInfo.class);
    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static void init() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                fetchCustomDataTypes();
            }
        }, 0, 5, TimeUnit.MINUTES);

    }

    public static void fetchCustomDataTypes() {
        Context.accountId.set(1_000_000);
        try {
            List<CustomDataType> customDataTypes = CustomDataTypeDao.instance.findAll(new BasicDBObject());
            Map<String, CustomDataType> newMap = new HashMap<>();
            List<CustomDataType> sensitiveCustomDataType = new ArrayList<>();
            List<CustomDataType> nonSensitiveCustomDataType = new ArrayList<>();
            for (CustomDataType customDataType: customDataTypes) {
                newMap.put(customDataType.getName(), customDataType);
                if (customDataType.isSensitiveAlways()) {
                    sensitiveCustomDataType.add(customDataType);
                } else {
                    nonSensitiveCustomDataType.add(customDataType);
                }
            }
            customDataTypeMap = newMap;
            sensitiveCustomDataType.addAll(nonSensitiveCustomDataType);
            customDataTypesSortedBySensitivity = new ArrayList<>(sensitiveCustomDataType);
        } catch (Exception ex) {
            ex.printStackTrace(); // or logger would be better
        }
    }


    public enum SuperType {
        BOOLEAN, INTEGER, FLOAT, STRING, NULL, OTHER, CUSTOM
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
    public static final SubType EMAIL = new SubType("EMAIL", true, SuperType.STRING, EmailSchema.class,
            Collections.emptyList());
    public static final SubType URL = new SubType("URL", false, SuperType.STRING, StringSchema.class,
            Collections.emptyList());
    public static final SubType ADDRESS = new SubType("ADDRESS", true, SuperType.STRING, StringSchema.class,
            Collections.emptyList());
    public static final SubType SSN = new SubType("SSN", true, SuperType.STRING, StringSchema.class,
            Collections.emptyList());
    public static final SubType CREDIT_CARD = new SubType("CREDIT_CARD", true, SuperType.STRING, StringSchema.class,
            Collections.emptyList());
    public static final SubType PHONE_NUMBER = new SubType("PHONE_NUMBER", true, SuperType.STRING, StringSchema.class,
            Collections.emptyList());
    public static final SubType UUID = new SubType("UUID", true, SuperType.STRING, StringSchema.class,
            Collections.emptyList());
    public static final SubType GENERIC = new SubType("GENERIC", false, SuperType.STRING, StringSchema.class,
            Collections.emptyList());
    public static final SubType DICT = new SubType("DICT", false, SuperType.STRING, MapSchema.class,
            Collections.emptyList());
    public static final SubType JWT = new SubType("JWT", false, SuperType.STRING, StringSchema.class,
            Arrays.asList(Position.RESPONSE_PAYLOAD, Position.RESPONSE_HEADER));
    public static final SubType IP_ADDRESS = new SubType("IP_ADDRESS", false, SuperType.STRING, StringSchema.class,
            Arrays.asList(Position.RESPONSE_PAYLOAD, Position.RESPONSE_HEADER));
    // make sure to add AKTO subTypes to subTypeMap below

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

        public void setSubTypeString(String subTypeString) {
            this.subTypeString = subTypeString;
            this.subType = subTypeMap.get(subTypeString);
            if (this.subType == null) {
                CustomDataType customDataType = customDataTypeMap.get(subTypeString);
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
    }

    String url;
    String method;
    int responseCode;
    boolean isHeader;
    String param;
    @BsonIgnore
    SubType subType;
    @BsonProperty("subType")
    String subTypeString;
    Set<Object> examples = new HashSet<>();
    Set<String> userIds = new HashSet<>();
    int count;
    int timestamp;
    int duration;
    int apiCollectionId;
    @BsonIgnore
    boolean sensitive;
    boolean isUrlParam;
    public static final String VALUES = "values";
    public static final int VALUES_LIMIT = 50;
    CappedSet<String> values = new CappedSet<String>();
    public static final String DOMAIN = "domain";
    Domain domain = Domain.ENUM;
    public static final String MIN_VALUE = "minValue";
    public static final long acceptedMaxValue =  Long.MAX_VALUE - 100_000;
    long minValue = acceptedMaxValue; // this value will be used when field doesn't exist in db
    public static final String MAX_VALUE = "maxValue";
    public static final long acceptedMinValue =  Long.MIN_VALUE + 100_000;
    long maxValue = acceptedMinValue;  // this value will be used when field doesn't exist in db
    public static final String LAST_SEEN = "lastSeen";
    long lastSeen;

    public enum Domain {
        ENUM, RANGE, ANY
    }

    public static final Map<String, SubType> subTypeMap = new HashMap<>();
    public static Map<String, CustomDataType> customDataTypeMap = new HashMap<>();
    public static List<CustomDataType> customDataTypesSortedBySensitivity = new ArrayList<>();
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
                          int duration) {
        this.url = paramId.url;
        this.method = paramId.method;
        this.responseCode = paramId.responseCode;
        this.isHeader = paramId.isHeader;
        this.param = paramId.param;
        this.subType = paramId.subType;
        this.apiCollectionId = paramId.apiCollectionId;
        this.isUrlParam = paramId.isUrlParam;
        this.examples = examples;
        this.userIds = userIds;
        this.count = count;
        this.timestamp = timestamp;
        this.duration = duration;
        this.lastSeen = Context.now();
    }

    public String composeKey() {
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

        CappedSet<String> copyValues = new CappedSet<String>(new HashSet<>(this.values.getElements()));

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
                this.timestamp, this.duration);
        singleTypeInfo.setValues(copyValues);
        singleTypeInfo.minValue = this.minValue;
        singleTypeInfo.maxValue = this.maxValue;
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
            CustomDataType customDataType = customDataTypeMap.get(subTypeString);
            if (customDataType != null) {
                this.subType = customDataType.toSubType();
            } else {
                // TODO:
                this.subType = GENERIC;
            }
        }
    }

    public boolean getSensitive() {
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

    public boolean isUrlParam() {
        return isUrlParam;
    }

    public void setIsUrlParam(boolean urlParam) {
        isUrlParam = urlParam;
    }

    public void setMinMaxValues(Object o) {
        if (subType.getSuperType() == SingleTypeInfo.SuperType.INTEGER || subType.getSuperType() == SingleTypeInfo.SuperType.FLOAT) {
            try {
                // this is done so that both integer and decimal values can be parsed
                // But while storing double we omit the decimal part
                double d = Double.parseDouble(o.toString());
                long l = (long) d;
                this.minValue = min(this.minValue, l);
                if (this.minValue < acceptedMinValue) {
                    this.minValue = acceptedMinValue;
                }
                this.maxValue = max(this.maxValue, l);
                if (this.maxValue > acceptedMaxValue) {
                    this.maxValue = acceptedMaxValue;
                }
            } catch (Exception e) {
                logger.error("ERROR: while parsing long for min max in sti " + o.toString());
            }
        }
    }

    public void merge(SingleTypeInfo that) {
        if (that != null) {
            this.count += that.getCount();
            this.values.getElements().addAll(that.values.getElements());
            this.minValue = min(this.minValue, that.minValue);
            this.maxValue = max(this.maxValue, that.maxValue);
            this.lastSeen = max(this.lastSeen, that.lastSeen);
        }
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

}
