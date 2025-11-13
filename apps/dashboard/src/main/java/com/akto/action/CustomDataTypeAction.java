package com.akto.action;


import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.Predicate;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.listener.InitializerListener;
import com.akto.listener.RuntimeListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.parsers.HttpCallParser;
import com.akto.testing.TemplateMapper;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.akto.util.JSONUtils;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.utils.AccountHTTPCallParserAktoPolicyInfo;
import com.akto.utils.AktoCustomException;
import com.akto.utils.RedactSampleData;
import com.akto.utils.Utils;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mongodb.BasicDBObject;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import com.opensymphony.xwork2.Action;
import org.apache.commons.lang3.EnumUtils;
import org.bson.conversions.Bson;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.akto.dto.type.SingleTypeInfo.fetchCustomDataTypes;
import static com.akto.dto.type.SingleTypeInfo.subTypeMap;
import static com.akto.utils.Utils.extractJsonResponse;

public class CustomDataTypeAction extends UserAction{
    private static final LoggerMaker loggerMaker = new LoggerMaker(CustomDataTypeAction.class, LogDb.DASHBOARD);

    private static final ExecutorService service = Executors.newFixedThreadPool(1);
    private boolean createNew;
    private String name;
    private boolean sensitiveAlways;
    private List<String> sensitivePosition;
    private String operator;

    private String keyOperator;
    private List<ConditionFromUser> keyConditionFromUsers;

    private String valueOperator;
    private List<ConditionFromUser> valueConditionFromUsers;
    private boolean redacted;
    private boolean skipDataTypeTestTemplateMapping;

    private String iconString;
    private List<String> categoriesList;
    private Severity dataTypePriority;

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
    public String fetchDataTypesForSettings() {
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
        List<AktoDataType> aktoDataTypes = AktoDataTypeDao.instance.findAll(new BasicDBObject());
        dataTypes.put("aktoDataTypes", aktoDataTypes);

        return Action.SUCCESS.toUpperCase();
    }

    public String fillSensitiveDataTypes() {
        try {
            InitializerListener.insertPiiSources();
        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.errorAndAddToDb("error in insertPiiSources " + e.getMessage());
        }
        try {
            InitializerListener.executePIISourceFetch();
        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.errorAndAddToDb("error in executePIISourceFetch " + e.getMessage());
        }

        return Action.SUCCESS.toUpperCase();
    }

    List<String> allDataTypes;
    public String fetchDataTypeNames() {
        this.allDataTypes = new ArrayList<>();
        List<CustomDataType> customDataTypes = CustomDataTypeDao.instance.findAll(new BasicDBObject());
        for (CustomDataType cdt: customDataTypes) {
            allDataTypes.add(cdt.getName());
        }
        for (SingleTypeInfo.SubType subType: subTypeMap.values()) {
            allDataTypes.add(subType.getName());
        }

        return Action.SUCCESS.toUpperCase();
    }

    public BasicDBObject getDataTypes() {
        return dataTypes;
    }

    private CustomDataType customDataType;

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

        try {
            customDataType.validateRaw("some_key", "some_value");
        } catch (Exception e) {
            addActionError("There is something wrong in the data type conditions");
            return ERROR.toUpperCase();
        }
        
        CustomDataType customDataTypeFromDb = CustomDataTypeDao.instance.findOne(Filters.eq(CustomDataType.NAME, name));
        if (this.createNew) {
            if (customDataTypeFromDb != null) {
                addActionError("Data type with same name exists");
                return ERROR.toUpperCase();
            }
            // id is automatically set when inserting in pojo
            CustomDataTypeDao.instance.insertOne(customDataType);
        } else {

            if (customDataTypeFromDb!=null && customDataTypeFromDb.getCreatorId() == 1638571050) {
                customDataType.setUserModifiedTimestamp(Context.now());
            }
            
            FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
            options.returnDocument(ReturnDocument.AFTER);
            options.upsert(false);
            customDataType = CustomDataTypeDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq("name", customDataType.getName()),
                Updates.combine(
                    Updates.set(CustomDataType.SENSITIVE_ALWAYS,customDataType.isSensitiveAlways()),
                    Updates.set(CustomDataType.SENSITIVE_POSITION,customDataType.getSensitivePosition()),
                    Updates.set(CustomDataType.KEY_CONDITIONS,customDataType.getKeyConditions()),
                    Updates.set(CustomDataType.VALUE_CONDITIONS,customDataType.getValueConditions()),
                    Updates.set(CustomDataType.OPERATOR,customDataType.getOperator()),
                    Updates.set(CustomDataType.TIMESTAMP,Context.now()),
                    Updates.set(CustomDataType.ACTIVE,active),
                    Updates.set(CustomDataType.REDACTED,customDataType.isRedacted()),
                    Updates.set(CustomDataType.SKIP_DATA_TYPE_TEST_TEMPLATE_MAPPING,skipDataTypeTestTemplateMapping),
                    Updates.set(CustomDataType.SAMPLE_DATA_FIXED,customDataType.isSampleDataFixed()),
                    Updates.set(AktoDataType.CATEGORIES_LIST, customDataType.getCategoriesList()),
                    Updates.set(AktoDataType.DATA_TYPE_PRIORITY, customDataType.getDataTypePriority()),
                    Updates.set(CustomDataType.ICON_STRING, customDataType.getIconString()),
                    Updates.set(CustomDataType.USER_MODIFIED_TIMESTAMP, customDataType.getUserModifiedTimestamp())
                ),
                options
            );

            if (customDataType == null) {
                addActionError("Failed to update data type");
                return ERROR.toUpperCase();
            }
        }

        SingleTypeInfo.fetchCustomDataTypes(Context.accountId.get());
        SingleTypeInfo.SubType currentSubType = customDataType.toSubType();

        if(redacted){
            int accountId = Context.accountId.get();
            service.submit(() ->{
                Context.accountId.set(accountId);
                loggerMaker.debugAndAddToDb("Triggered a job to fix existing custom data types", LogDb.DASHBOARD);
                handleRedactionForSubType(currentSubType, true);
            });
        }

        if (!customDataType.isSkipDataTypeTestTemplateMapping()) {
            int accountId = Context.accountId.get();
            service.submit(() -> {
                Context.accountId.set(accountId);
                loggerMaker.debugAndAddToDb("Triggered a job to update test template based on akto data type " + name,
                        LogDb.DASHBOARD);
                TemplateMapper templateMapper = new TemplateMapper();
                templateMapper.createTestTemplateForCustomDataType(customDataType);
            });
        }

        return Action.SUCCESS.toUpperCase();
    }

    private AktoDataType aktoDataType;
    
    public String saveAktoDataType() {

        aktoDataType = AktoDataTypeDao.instance.findOne("name",name);
        if(aktoDataType==null){
            addActionError("invalid data type");
            return ERROR.toUpperCase();
        }
        
        List<SingleTypeInfo.Position> sensitivePositions = new ArrayList<>();
        try {
            sensitivePositions = generatePositions(sensitivePosition);
        } catch (Exception ignored) {
            addActionError("Invalid positions for sensitive data");
            return ERROR.toUpperCase();
        }    

        Conditions keyConditions = null;
        Conditions valueConditions = null;

        try {
            keyConditions = generateKeyConditions();
        } catch (AktoCustomException e) {
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }

        try {
            valueConditions = generateValueConditions();
        } catch (AktoCustomException e) {
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }

        Conditions.Operator mainOperator;
        try {
            mainOperator = Conditions.Operator.valueOf(operator);
        } catch (Exception ignored) {
            addActionError("Invalid value operator");
            return ERROR.toUpperCase();
        }


        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.returnDocument(ReturnDocument.AFTER);
        options.upsert(false);
        aktoDataType = AktoDataTypeDao.instance.getMCollection().findOneAndUpdate(
            Filters.eq("name", aktoDataType.getName()),
            Updates.combine(
                Updates.set("sensitiveAlways",sensitiveAlways),
                Updates.set("sensitivePosition",sensitivePositions),
                Updates.set("timestamp",Context.now()),
                Updates.set("redacted",redacted),
                Updates.set(AktoDataType.SAMPLE_DATA_FIXED, !redacted),
                Updates.set(AktoDataType.CATEGORIES_LIST, Utils.getUniqueValuesOfList(categoriesList)),
                Updates.set(AktoDataType.KEY_CONDITIONS, keyConditions),
                Updates.set(AktoDataType.VALUE_CONDITIONS, valueConditions),
                Updates.set(AktoDataType.OPERATOR, mainOperator),
                Updates.set(AktoDataType.DATA_TYPE_PRIORITY, dataTypePriority),
                Updates.set(AktoDataType._INACTIVE, !active)
            ),
            options
        );

        if (aktoDataType == null) {
            addActionError("Failed to update data type");
            return ERROR.toUpperCase();
        }

        SingleTypeInfo.fetchCustomDataTypes(Context.accountId.get());
        SingleTypeInfo.SubType currentSubType = subTypeMap.get(aktoDataType.getName());
        if(redacted){
            int accountId = Context.accountId.get();
            service.submit(() ->{
                Context.accountId.set(accountId);
                loggerMaker.debugAndAddToDb("Triggered a job to fix existing akto data types", LogDb.DASHBOARD);
                handleRedactionForSubType(currentSubType, true);
            });
        }

        int accountId = Context.accountId.get();
        service.submit(() ->{
            Context.accountId.set(accountId);
            loggerMaker.debugAndAddToDb("Triggered a job to update test template based on akto data type " + name, LogDb.DASHBOARD);
            TemplateMapper templateMapper = new TemplateMapper();
            templateMapper.createTestTemplateForAktoDataType(aktoDataType);
        });

        return Action.SUCCESS.toUpperCase();
    }

    public String resetSampleData(){
        try {
            int limit = 30;
            List<SampleData> sampleDataList = new ArrayList<>();
            loggerMaker.debugAndAddToDb("triggered sample data redaction cron", LogDb.DASHBOARD);
            String lastFetchedUrl = null;
            String lastFetchedMethod = null;
            while (true) {
                ArrayList<WriteModel<SampleData>> bulkUpdatesForSampleData = new ArrayList<>();
                sampleDataList = SampleDataDao.instance.fetchSampleDataPaginated(lastFetchedUrl, lastFetchedMethod, limit);
                if (sampleDataList == null || sampleDataList.size() == 0) {
                    break;
                }

                loggerMaker.debugAndAddToDb("Read " + sampleDataList.size() + " samples", LogDb.DASHBOARD);

                for (SampleData sd: sampleDataList) {
                    lastFetchedUrl = sd.getId().getUrl();
                    lastFetchedMethod = sd.getId().getMethod().name();
                    List<String> samples = sd.getSamples();
                    if (samples == null || samples.size() == 0) {
                        continue;
                    }
                    List<String> newSamples = new ArrayList<>();
                    for (String sample: samples) {
                        newSamples.add(RedactSampleData.redactIfRequired(sample, false, false));
                    }
                    Bson bson = Updates.combine(
                        Updates.set("samples", newSamples)
                    );
                    Bson filters = Filters.and(
                        Filters.eq("_id.url", sd.getId().getUrl()),
                        Filters.eq("_id.method", sd.getId().getMethod()),
                        Filters.eq("_id.apiCollectionId", sd.getId().getApiCollectionId())
                    );
                    bulkUpdatesForSampleData.add(
                        new UpdateOneModel<>(
                                filters,
                                bson
                        )
                    );
                }
                if (bulkUpdatesForSampleData.size() > 0) {
                    SampleDataDao.instance.getMCollection().bulkWrite(bulkUpdatesForSampleData);
                }
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in redact data sd " + e.toString());
        }

        try {
            int limit = 30;
            List<SensitiveSampleData> sampleDataList = new ArrayList<>();
            int skip = 0;
            while (true) {
                ArrayList<WriteModel<SensitiveSampleData>> bulkUpdatesForSensitiveSampleData = new ArrayList<>();
                sampleDataList = SensitiveSampleDataDao.instance.findAll(Filters.empty(), skip, limit, null);
                if (sampleDataList == null || sampleDataList.size() == 0) {
                    break;
                }
                loggerMaker.debugAndAddToDb("Read " + sampleDataList.size() + " sensitive samples", LogDb.DASHBOARD);
                skip+=limit;
                for (SensitiveSampleData sd: sampleDataList) {
                    List<String> samples = sd.getSampleData();
                    if (samples == null || samples.size() == 0) {
                        continue;
                    }
                    List<String> newSamples = new ArrayList<>();
                    for (String sample: samples) {
                        newSamples.add(RedactSampleData.redactIfRequired(sample, false, false));
                    }
                    Bson sensitiveSampleBson = Updates.combine(
                        Updates.set("sampleData", newSamples)
                    );
                    Bson filters = Filters.and(
                        Filters.eq("_id.url", sd.getId().getUrl()),
                        Filters.eq("_id.method", sd.getId().getMethod()),
                        Filters.eq("_id.apiCollectionId", sd.getId().getApiCollectionId())
                    );
                    bulkUpdatesForSensitiveSampleData.add(
                        new UpdateOneModel<>(
                                filters,
                                sensitiveSampleBson
                        )
                    );
                }
                if (bulkUpdatesForSensitiveSampleData.size() > 0) {
                    SensitiveSampleDataDao.instance.getMCollection().bulkWrite(bulkUpdatesForSensitiveSampleData);
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in redact data ssd " + e.toString());
        }
        return Action.SUCCESS.toUpperCase();
    }

    public static void handleDataTypeRedaction(){
        try{
            fetchCustomDataTypes(Context.accountId.get());
            loggerMaker.debugAndAddToDb("Dropping redacted data types for custom data types", LogDb.DASHBOARD);
            List<CustomDataType> customDataTypesToBeFixed = CustomDataTypeDao.instance.findAll(Filters.eq(AktoDataType.SAMPLE_DATA_FIXED, false));

            List<AktoDataType> aktoDataTypesToBeFixed = AktoDataTypeDao.instance.findAll(Filters.eq(AktoDataType.SAMPLE_DATA_FIXED, false));
            loggerMaker.debugAndAddToDb("Found " + aktoDataTypesToBeFixed.size() + " akto data types to be fixed", LogDb.DASHBOARD);

            Set<SingleTypeInfo.SubType> subTypesToBeFixed = new HashSet<>();
            customDataTypesToBeFixed.forEach(cdt -> {
                SingleTypeInfo.SubType st = cdt.toSubType();
                subTypesToBeFixed.add(st);
            });
            aktoDataTypesToBeFixed.forEach(adt -> {
                SingleTypeInfo.SubType st = subTypeMap.get(adt.getName());
                subTypesToBeFixed.add(st);
            });
            subTypesToBeFixed.forEach(st -> {
                loggerMaker.debugAndAddToDb("Dropping redacted data types for subType:" + st.getName(), LogDb.DASHBOARD);
                handleRedactionForSubType(st, false);
                loggerMaker.debugAndAddToDb("Dropped redacted data types for subType:" + st.getName(), LogDb.DASHBOARD);
            });
            loggerMaker.debugAndAddToDb("Dropped redacted data types successfully!", LogDb.DASHBOARD);
        } catch (Exception e){
            loggerMaker.errorAndAddToDb("Failed to drop redacted data types");
        }

    }

    private static List<String> handleRedactionForSamples(List<String> samples) {
        List<String> newSamples = new ArrayList<>();    
        for(String sample : samples){
            try {
                String redactedSample = RedactSampleData.redactDataTypes(sample);
                newSamples.add(redactedSample);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in redact data types " + e.toString());
                continue;
            }
        }
        return newSamples;
    }

    private static void handleRedactionForSubType(SingleTypeInfo.SubType subType, boolean modifySampleData) {
        loggerMaker.debugAndAddToDb("Dropping redacted data types for subType:" + subType.getName(), LogDb.DASHBOARD);
        int skip = 0;
        int limit = 100;
        ArrayList<WriteModel<SampleData>> writesForSampleData = new ArrayList<>();
        ArrayList<WriteModel<SensitiveSampleData>> writesForSensitiveSampleData = new ArrayList<>();
        while (true) {
            List<ApiInfo.ApiInfoKey> apiInfoKeys = SingleTypeInfoDao.instance.fetchEndpointsBySubType(subType, skip, limit);
            if(apiInfoKeys.isEmpty()){
                loggerMaker.infoAndAddToDb("No apiInfoKeys left for subType:" + subType.getName(), LogDb.DASHBOARD);
                break;
            }

            loggerMaker.infoAndAddToDb("Found " + apiInfoKeys.size() + " apiInfoKeys for subType:" + subType.getName() + " and skip:" + skip, LogDb.DASHBOARD);
            List<Bson> query = new ArrayList<>();
            for(ApiInfo.ApiInfoKey key : apiInfoKeys){
                Bson basicFilter = ApiInfoDao.getFilter(key);
                query.add(basicFilter);
            }
            if(!modifySampleData){
                UpdateResult updateResult = SampleDataDao.instance.updateManyNoUpsert(Filters.or(query), Updates.set("samples", Collections.emptyList()));
                loggerMaker.debugAndAddToDb("Redacted samples in sd for subType:" + subType.getName() + ", modified:" + updateResult.getModifiedCount(), LogDb.DASHBOARD);

                updateResult = SensitiveSampleDataDao.instance.updateManyNoUpsert(Filters.or(query), Updates.set("sampleData", Collections.emptyList()));
                loggerMaker.debugAndAddToDb("Redacted samples in ssd for subType:" + subType.getName() + ", modified:" + updateResult.getModifiedCount(), LogDb.DASHBOARD);
            }else{
                List<SampleData> sampleDataList = SampleDataDao.instance.findAll(Filters.or(query));
                for(SampleData sampleData : sampleDataList){
                    List<String> newSamples = handleRedactionForSamples(sampleData.getSamples());
                    sampleData.setSamples(newSamples);
                    Bson filter = Filters.and(Filters.eq("_id.url", sampleData.getId().getUrl()), Filters.eq("_id.method", sampleData.getId().getMethod()), Filters.eq("_id.apiCollectionId", sampleData.getId().getApiCollectionId()));
                    writesForSampleData.add(new UpdateManyModel<>(filter, Updates.set("samples", newSamples)));
                }

                List<SensitiveSampleData> sensitiveSampleDataList = SensitiveSampleDataDao.instance.findAll(Filters.or(query));
                for(SensitiveSampleData sensitiveSampleData : sensitiveSampleDataList){
                    List<String> newSamples = handleRedactionForSamples(sensitiveSampleData.getSampleData());
                    sensitiveSampleData.setSampleData(newSamples);
                    Bson filter = Filters.and(Filters.eq("_id.url", sensitiveSampleData.getId().getUrl()), Filters.eq("_id.method", sensitiveSampleData.getId().getMethod()), Filters.eq("_id.apiCollectionId", sensitiveSampleData.getId().getApiCollectionId()));
                    writesForSensitiveSampleData.add(new UpdateManyModel<>(filter, Updates.set("sampleData", newSamples)));
                }
            }
            if(!writesForSampleData.isEmpty()){
                BulkWriteResult result =  SampleDataDao.instance.getMCollection().bulkWrite(writesForSampleData);
                loggerMaker.infoAndAddToDb("Redaction results: matched count:" + result.getMatchedCount() + " modified count:" + result.getModifiedCount() + " samples modified in sd for subType:" + subType.getName());
            }
            if(!writesForSensitiveSampleData.isEmpty()){
                
                BulkWriteResult resultSensitive = SensitiveSampleDataDao.instance.getMCollection().bulkWrite(writesForSensitiveSampleData);
                loggerMaker.infoAndAddToDb("Redaction results: matched count:" + resultSensitive.getMatchedCount() + " modified count:" + resultSensitive.getModifiedCount() + " sensitive samples modified in sd for subType:" + subType.getName());
            }
            writesForSampleData.clear();
            writesForSensitiveSampleData.clear();
            skip += limit;
        }
        UpdateResult updateResult = SingleTypeInfoDao.instance.updateManyNoUpsert(Filters.and(Filters.eq("subType", subType.getName()), Filters.exists("values.elements", true)), Updates.set("values.elements", Collections.emptyList()));
        loggerMaker.debugAndAddToDb("Redacted values in sti for subType:" + subType.getName() + ", modified:" + updateResult.getModifiedCount(), LogDb.DASHBOARD);

    }

    public List<SingleTypeInfo.Position> generatePositions(List<String> sensitivePosition){
        
        Set<SingleTypeInfo.Position> sensitivePositionSet = new HashSet<>();
        if(sensitivePosition!=null && sensitivePosition.size()>0){
            for(String s:sensitivePosition){
                if(EnumUtils.isValidEnumIgnoreCase(SingleTypeInfo.Position.class, s)){
                    sensitivePositionSet.add(SingleTypeInfo.Position.valueOf(s.toUpperCase()));
                }
            }
        }
        List<SingleTypeInfo.Position> sensitivePositions = new ArrayList<SingleTypeInfo.Position>(sensitivePositionSet);
        return sensitivePositions;
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

    public String resetDataTypeRetro() {
        customDataType = CustomDataTypeDao.instance.findOne(CustomDataType.NAME, name);
        aktoDataType = AktoDataTypeDao.instance.findOne(AktoDataType.NAME, name);

        if (customDataType == null && aktoDataType == null) {
            addActionError("Data type does not exist");
            return ERROR.toUpperCase();
        }

        /*
         * we find all samples which contain the data type 
         * and re-run runtime on the samples.
         */

        int accountId = Context.accountId.get();
        service.submit(() -> {
            Context.accountId.set(accountId);
            loggerMaker.debugAndAddToDb("Triggered a job to recalculate data types");
            int skip = 0;
            final int LIMIT = 100;
            final int BATCH_SIZE = 200;
            List<SampleData> sampleDataList = new ArrayList<>();
            Bson sort = Sorts.ascending("_id.apiCollectionId", "_id.url", "_id.method");    
            List<HttpResponseParams> responses = new ArrayList<>();
            this.customSubTypeMatches = new ArrayList<>();

            SensitiveSampleDataDao.instance.getMCollection().deleteMany(Filters.eq("_id.subType", name));
            SingleTypeInfoDao.instance.updateMany(Filters.eq(SingleTypeInfo.SUB_TYPE, name),
                    Updates.set(SingleTypeInfo.SUB_TYPE, SingleTypeInfo.GENERIC.getName()));

            do {
                sampleDataList = SampleDataDao.instance.findAll(Filters.empty(), skip, LIMIT, sort);
                skip += LIMIT;
                for(SampleData sampleData : sampleDataList){
                    List<String> samples = sampleData.getSamples();
                    Set<String> foundSet = new HashSet<>();
                    for (String sample: samples) {
                        Key apiKey = sampleData.getId();
                        try {
                            HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(sample);
                            boolean skip1=false, skip2=false, skip3=false, skip4=false;
                            try {
                                skip1 = forHeaders(httpResponseParams.getHeaders(), customDataType, apiKey);
                                skip2 = forHeaders(httpResponseParams.requestParams.getHeaders(), customDataType, apiKey);
                            } catch (Exception e) {
                            }
                            try {
                                skip3 = forPayload(httpResponseParams.getPayload(), customDataType, apiKey);
                                skip4 = forPayload(httpResponseParams.requestParams.getPayload(), customDataType, apiKey);
                            } catch (Exception e) {
                            }
                            String key = skip1 + " " + skip2 + " " + skip3 + " " + skip4 + " " + sampleData.getId().toString();
                            if ((skip1 || skip2 || skip3 || skip4) && !foundSet.contains(key)) {
                                foundSet.add(key);
                                responses.add(httpResponseParams);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                if (sampleDataList != null) {
                    loggerMaker.debugAndAddToDb("Read sampleData to recalculate data types " + sampleDataList.size());
                }

                if (!responses.isEmpty() && responses.size() >= BATCH_SIZE) {
                    loggerMaker.debugAndAddToDb(
                            "Starting processing responses to recalculate data types " + responses.size());
                    SingleTypeInfo.fetchCustomDataTypes(accountId);
                    AccountHTTPCallParserAktoPolicyInfo info = RuntimeListener.accountHTTPParserMap.get(accountId);
                    if (info == null) {
                        info = new AccountHTTPCallParserAktoPolicyInfo();
                        HttpCallParser callParser = new HttpCallParser("userIdentifier", 1, 1, 1, false);
                        info.setHttpCallParser(callParser);
                        RuntimeListener.accountHTTPParserMap.put(accountId, info);
                    }
                    AccountSettings accountSettings = AccountSettingsDao.instance
                            .findOne(AccountSettingsDao.generateFilter());
                    responses = com.akto.runtime.Main.filterBasedOnHeaders(responses, accountSettings);
                    info.getHttpCallParser().syncFunction(responses, true, false, accountSettings);
                    info.getHttpCallParser().apiCatalogSync.buildFromDB(false, false);
                    loggerMaker.debugAndAddToDb("Processed responses to recalculate data types " + responses.size());
                    responses.clear();
                }

            }while(sampleDataList!=null && !sampleDataList.isEmpty());

            if (!responses.isEmpty()) {
                loggerMaker.debugAndAddToDb("Starting processing responses to recalculate data types " + responses.size());
                SingleTypeInfo.fetchCustomDataTypes(accountId);
                AccountHTTPCallParserAktoPolicyInfo info = RuntimeListener.accountHTTPParserMap.get(accountId);
                if (info == null) {
                    info = new AccountHTTPCallParserAktoPolicyInfo();
                    HttpCallParser callParser = new HttpCallParser("userIdentifier", 1, 1, 1, false);
                    info.setHttpCallParser(callParser);
                    RuntimeListener.accountHTTPParserMap.put(accountId, info);
                }
                AccountSettings accountSettings = AccountSettingsDao.instance
                        .findOne(AccountSettingsDao.generateFilter());
                responses = com.akto.runtime.Main.filterBasedOnHeaders(responses, accountSettings);
                info.getHttpCallParser().syncFunction(responses, true, false, accountSettings);
                info.getHttpCallParser().apiCatalogSync.buildFromDB(false, false);
                loggerMaker.debugAndAddToDb("Processed responses to recalculate data types " + responses.size());
            }
            loggerMaker.debugAndAddToDb("Finished a job to recalculate data types");
        });

        return SUCCESS.toUpperCase();
    }

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
                    boolean skip1 = ( customDataType.isSensitiveAlways() || customDataType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_HEADER) ) ? forHeaders(httpResponseParams.getHeaders(), customDataType, apiKey) : false;
                    boolean skip2 = ( customDataType.isSensitiveAlways() || customDataType.getSensitivePosition().contains(SingleTypeInfo.Position.REQUEST_HEADER) ) ? forHeaders(httpResponseParams.requestParams.getHeaders(), customDataType, apiKey) : false;
                    boolean skip3 = ( customDataType.isSensitiveAlways() || customDataType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_PAYLOAD) ) ? forPayload(httpResponseParams.getPayload(), customDataType, apiKey) : false;
                    boolean skip4 = ( customDataType.isSensitiveAlways() || customDataType.getSensitivePosition().contains(SingleTypeInfo.Position.REQUEST_PAYLOAD) ) ? forPayload(httpResponseParams.requestParams.getPayload(), customDataType, apiKey) : false;
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

    static class MatchResult {
        String key;
        Object value;

        public MatchResult(String key, Object value) {
            this.key = key;
            this.value = value;
        }
    }

    public static void extractAllValuesFromPayload(JsonNode node, String key, CustomDataType customDataType, List<MatchResult> matches) {
        if (node == null) return;
        if (node.isValueNode()) {
            Object value = mapper.convertValue(node, Object.class);
            boolean result = customDataType.validate(value, key);
            if (result) matches.add(new MatchResult(key, value));
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for(int i = 0; i < arrayNode.size(); i++) {
                JsonNode arrayElement = arrayNode.get(i);
                extractAllValuesFromPayload(arrayElement, null, customDataType, matches);
            }
        } else {
            Iterator<String> fieldNames = node.fieldNames();
            while(fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode fieldValue = node.get(fieldName);
                extractAllValuesFromPayload(fieldValue, fieldName, customDataType, matches);
            }
        }

    }

    public boolean forPayload(String payload, CustomDataType customDataType, Key apiKey) throws IOException {
        JsonParser jp = factory.createParser(payload);
        JsonNode node = mapper.readTree(jp);

        List<MatchResult> matchResults = new ArrayList<>();
        extractAllValuesFromPayload(node,null, customDataType, matchResults);

        for (MatchResult matchResult: matchResults) {
            CustomSubTypeMatch customSubTypeMatch = new CustomSubTypeMatch(
                    apiKey.getApiCollectionId(),apiKey.url,apiKey.method.name(), matchResult.key,  matchResult.value.toString()
            );
            this.customSubTypeMatches.add(customSubTypeMatch);
        }

        return matchResults.size() > 0;

    }

    public Conditions generateKeyConditions() throws AktoCustomException {
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
                Predicate predicate = Predicate.generatePredicate(conditionFromUser.type, conditionFromUser.valueMap);
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

        return keyConditions;
    }

    public Conditions generateValueConditions() throws AktoCustomException {
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
                Predicate predicate = Predicate.generatePredicate(conditionFromUser.type, conditionFromUser.valueMap);
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

        return valueConditions;
    }

    public CustomDataType generateCustomDataType(int userId) throws AktoCustomException {
        // TODO: handle errors
        if (name == null || name.length() == 0) throw new AktoCustomException("Name cannot be empty");
        int maxChars = 25;
        if (name.length() > maxChars) throw new AktoCustomException("Maximum length allowed is "+maxChars+" characters");
        name = name.trim();
        name = name.toUpperCase();
        if (!(name.matches("[A-Z_0-9 ]+"))) throw new AktoCustomException("Name can only contain alphabets, spaces, numbers and underscores");

        if (subTypeMap.containsKey(name)) {
            throw new AktoCustomException("Data type name reserved");
        }


        Conditions keyConditions = generateKeyConditions();
        Conditions valueConditions = generateValueConditions();
        

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

        List<SingleTypeInfo.Position> sensitivePositions = new ArrayList<>();
        try {
            sensitivePositions = generatePositions(sensitivePosition);
        } catch (Exception ignored) {
            throw new AktoCustomException("Invalid positions for sensitive data");
        }

        IgnoreData ignoreData = new IgnoreData();
        CustomDataType dataType = new CustomDataType(name, sensitiveAlways, sensitivePositions, userId,
                true,keyConditions,valueConditions, mainOperator,ignoreData, redacted, !redacted);
        
        dataType.setCategoriesList(Utils.getUniqueValuesOfList(categoriesList));
        dataType.setIconString(iconString);
        dataType.setDataTypePriority(dataTypePriority);
        return dataType;
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

    public AktoDataType getAktoDataType() {
        return aktoDataType;
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

    private static void bulkSingleTypeInfoDelete(List<SingleTypeInfo.ParamId> idsToDelete) {
        ArrayList<WriteModel<SingleTypeInfo>> bulkUpdatesForSingleTypeInfo = new ArrayList<>();
        for(SingleTypeInfo.ParamId paramId: idsToDelete) {
            String paramStr = "PII cleaner - deleting: " + paramId.getApiCollectionId() + ": " + paramId.getMethod() + " " + paramId.getUrl() + " > " + paramId.getParam();
            loggerMaker.debugAndAddToDb(paramStr, LogDb.DASHBOARD);

            List<Bson> filters = new ArrayList<>();
            filters.add(Filters.eq("url", paramId.getUrl()));
            filters.add(Filters.eq("method", paramId.getMethod()));
            filters.add(Filters.eq("responseCode", paramId.getResponseCode()));
            filters.add(Filters.eq("isHeader", paramId.getIsHeader()));
            filters.add(Filters.eq("param", paramId.getParam()));
            filters.add(Filters.eq("apiCollectionId", paramId.getApiCollectionId()));

            bulkUpdatesForSingleTypeInfo.add(new DeleteOneModel<>(Filters.and(filters)));
        }

        if (!bulkUpdatesForSingleTypeInfo.isEmpty()) {
            BulkWriteResult bwr =
                    SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkUpdatesForSingleTypeInfo, new BulkWriteOptions().ordered(false));

            loggerMaker.debugAndAddToDb("PII cleaner - deleted " + bwr.getDeletedCount() + " from STI", LogDb.DASHBOARD);
        }

    }

    private static void bulkSensitiveInvalidate(List<SingleTypeInfo.ParamId> idsToDelete) {
        ArrayList<WriteModel<SensitiveSampleData>> bulkSensitiveInvalidateUpdates = new ArrayList<>();
        for(SingleTypeInfo.ParamId paramId: idsToDelete) {
            String paramStr = "PII cleaner - invalidating: " + paramId.getApiCollectionId() + ": " + paramId.getMethod() + " " + paramId.getUrl() + " > " + paramId.getParam();
            String url = "dashboard/observe/inventory/"+paramId.getApiCollectionId()+"/"+Base64.getEncoder().encodeToString((paramId.getUrl() + " " + paramId.getMethod()).getBytes());
            loggerMaker.debugAndAddToDb(paramStr + url, LogDb.DASHBOARD);

            List<Bson> filters = new ArrayList<>();
            filters.add(Filters.eq("_id.url", paramId.getUrl()));
            filters.add(Filters.eq("_id.method", paramId.getMethod()));
            filters.add(Filters.eq("_id.responseCode", paramId.getResponseCode()));
            filters.add(Filters.eq("_id.isHeader", paramId.getIsHeader()));
            filters.add(Filters.eq("_id.param", paramId.getParam()));
            filters.add(Filters.eq("_id.apiCollectionId", paramId.getApiCollectionId()));

            bulkSensitiveInvalidateUpdates.add(new UpdateOneModel<>(Filters.and(filters), Updates.set("invalid", true)));
        }

        if (!bulkSensitiveInvalidateUpdates.isEmpty()) {
            BulkWriteResult bwr =
                    SensitiveSampleDataDao.instance.getMCollection().bulkWrite(bulkSensitiveInvalidateUpdates, new BulkWriteOptions().ordered(false));

            loggerMaker.debugAndAddToDb("PII cleaner - modified " + bwr.getModifiedCount() + " from SSD", LogDb.DASHBOARD);
        }

    }

    private static boolean isPresentInMost(String param, List<String> commonPayloads, boolean isRequest) {
        int totalPayloads = commonPayloads.size();
        if (totalPayloads == 0) {
            return false;
        }

        int positiveCount = 0;

        for(String commonPayload: commonPayloads) {
            BasicDBObject commonPayloadObj = extractJsonResponse(commonPayload, isRequest);
            if (JSONUtils.flatten(commonPayloadObj).containsKey(param)) {
                positiveCount++;
            }
        }

        return positiveCount >= (totalPayloads+0.5)/2;
    }

    public String resetDataType() {
        final int BATCH_SIZE = 100;
        int currMarker = 0;
        Bson filterSsdQ =
                Filters.and(
                        Filters.eq("_id.isHeader", false),
                        Filters.ne("inactive", true),
                        Filters.eq("_id.subType", this.name)
                );

        MongoCursor<SensitiveSampleData> cursor = null;
        int dataPoints = 0;
        List<SingleTypeInfo.ParamId> idsToDelete = new ArrayList<>();
        do {
            idsToDelete = new ArrayList<>();
            Bson collectionFilter = Filters.empty();
            try {
                List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
                if(collectionIds != null) {
                    collectionFilter = Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds);
                }
            } catch(Exception e){
            }
            cursor = SensitiveSampleDataDao.instance.getMCollection().find(Filters.and(filterSsdQ, collectionFilter)).projection(Projections.exclude(SensitiveSampleData.SAMPLE_DATA)).skip(currMarker).limit(BATCH_SIZE).cursor();
            currMarker += BATCH_SIZE;
            dataPoints = 0;
            loggerMaker.debugAndAddToDb("processing batch: " + currMarker, LogDb.DASHBOARD);
            while(cursor.hasNext()) {
                dataPoints++;
                SensitiveSampleData ssd = cursor.next();
                SingleTypeInfo.ParamId ssdId = ssd.getId();

                if (ssdId.getIsHeader()) {
                    continue;
                }

                Bson filterCommonSampleData =
                        Filters.and(
                                Filters.eq("_id.method", ssdId.getMethod()),
                                Filters.eq("_id.url", ssdId.getUrl()),
                                Filters.eq("_id.apiCollectionId", ssdId.getApiCollectionId())
                        );


                SampleData commonSampleData = SampleDataDao.instance.findOne(filterCommonSampleData);

                if (commonSampleData == null) {
                    continue;
                }

                List<String> commonPayloads = commonSampleData.getSamples();

                if (!isPresentInMost(ssdId.getParam(), commonPayloads, ssdId.getResponseCode()==-1)) {
                    idsToDelete.add(ssdId);
                    loggerMaker.debugAndAddToDb("deleting: " + ssdId, LogDb.DASHBOARD);
                }
            }

            bulkSensitiveInvalidate(idsToDelete);
            bulkSingleTypeInfoDelete(idsToDelete);

        } while (dataPoints == BATCH_SIZE);


        return SUCCESS.toUpperCase();

    }
    
    BasicDBObject response;

    public String getCountOfApiVsDataType(){
        this.response = new BasicDBObject();
        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$apiCollectionId")
                                    .append(SingleTypeInfo._URL, "$url")
                                    .append(SingleTypeInfo._METHOD, "$method");
        Bson customFilter = Filters.nin(SingleTypeInfo._API_COLLECTION_ID, UsageMetricCalculator.getDeactivated());

        List<String> sensitiveSubtypes = SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames();
        sensitiveSubtypes.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
        List<String> sensitiveSubtypesInRequest = SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames();

        sensitiveSubtypes.addAll(sensitiveSubtypesInRequest);
        List<Bson> pipeline = SingleTypeInfoDao.instance.generateFilterForSubtypes(sensitiveSubtypes, groupedId, false, customFilter);

        try {
            MongoCursor<BasicDBObject> cursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
            Map<String,Integer> countOfApisVsDataType = new HashMap<>();
            Map<String,Set<Integer>> apiCollectionsMap = new HashMap<>();
            int count = 0;
            while (cursor.hasNext()) {
                count++;
                BasicDBObject bDbObject = cursor.next();
                BasicDBObject id = (BasicDBObject) bDbObject.get(Constants.ID);
                List<String> subTypes = (List<String>) bDbObject.get("subTypes");
                for(String subType: subTypes){
                    int initialCount = countOfApisVsDataType.getOrDefault(subType, 0);
                    Set<Integer> collSet = apiCollectionsMap.getOrDefault(subType, new HashSet<>());
                    countOfApisVsDataType.put(subType, initialCount + 1);
                    collSet.add(id.getInt(SingleTypeInfo._API_COLLECTION_ID));
                    apiCollectionsMap.put(subType, collSet);
                }
            }
            response.put("countMap", countOfApisVsDataType);
            response.put("totalApisCount", count);
            response.put("apiCollectionsMap", apiCollectionsMap);
        } catch (Exception e) {
            addActionError("Error in fetching subtypes count");
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
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

    public List<String> getAllDataTypes() {
        return allDataTypes;
    }

    public List<String> getSensitivePosition() {
        return sensitivePosition;
    }

    public void setSensitivePosition(List<String> sensitivePosition) {
        this.sensitivePosition = sensitivePosition;
    }

    public boolean getRedacted() {
        return redacted;
    }

    public void setRedacted(boolean redacted) {
        this.redacted = redacted;
    }

    public boolean isSkipDataTypeTestTemplateMapping() {
        return skipDataTypeTestTemplateMapping;
    }

    public void setSkipDataTypeTestTemplateMapping(boolean skipDataTypeTestTemplateMapping) {
        this.skipDataTypeTestTemplateMapping = skipDataTypeTestTemplateMapping;
    }
    public BasicDBObject getResponse() {
        return response;
    }

    public void setIconString(String iconString) {
        this.iconString = iconString;
    }

    public void setCategoriesList(List<String> categoriesList) {
        this.categoriesList = categoriesList;
    }

    public void setDataTypePriority(Severity dataTypePriority) {
        this.dataTypePriority = dataTypePriority;
    }

}