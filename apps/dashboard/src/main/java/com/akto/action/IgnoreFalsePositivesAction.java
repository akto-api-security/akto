package com.akto.action;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.dao.AktoDataTypeDao;
import com.akto.dao.CustomDataTypeDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.CustomDataType;
import com.akto.dto.IgnoreData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.SingleTypeInfo.ParamId;
import com.akto.dto.type.SingleTypeInfo.SubType;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

public class IgnoreFalsePositivesAction extends UserAction{
    private Map<String,IgnoreData> falsePositives;

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private SubType createSubType(String keyType){
        if(keyType.equals(SingleTypeInfo.CREDIT_CARD.getName()) || keyType.equals(SingleTypeInfo.PHONE_NUMBER.getName())){
            return SingleTypeInfo.INTEGER_64;
        }
        return SingleTypeInfo.GENERIC;
    }

    public String setFalsePositivesInSensitiveData() {
        if(falsePositives==null){
            return Action.ERROR.toUpperCase();
        }
        int accountId = Context.accountId.get();
        for (String keyType : falsePositives.keySet()) {
            if (SingleTypeInfo.getCustomDataTypeMap(accountId).containsKey(keyType)) {
                IgnoreData ignoreData = SingleTypeInfo.getCustomDataTypeMap(accountId).get(keyType).getIgnoreData();
                if (ignoreData == null) {
                    ignoreData = new IgnoreData(new HashMap<>(), new HashSet<>());
                }
                ignoreData.getIgnoredKeysInAllAPIs().addAll(falsePositives.get(keyType).getIgnoredKeysInAllAPIs());
                ignoreData.getIgnoredKeysInSelectedAPIs().putAll(falsePositives.get(keyType).getIgnoredKeysInSelectedAPIs());
                CustomDataTypeDao.instance.updateOne(Filters.eq(CustomDataType.NAME, keyType),
                    Updates.combine(
                            Updates.set(CustomDataType.IGNORE_DATA, ignoreData),
                            Updates.set(CustomDataType.TIMESTAMP, Context.now())));
            } else if (SingleTypeInfo.getAktoDataTypeMap(accountId).containsKey(keyType)) {
                IgnoreData ignoreData = SingleTypeInfo.getAktoDataTypeMap(accountId).get(keyType).getIgnoreData();
                if (ignoreData == null) {
                    ignoreData = new IgnoreData(new HashMap<>(), new HashSet<>());
                }
                ignoreData.getIgnoredKeysInAllAPIs().addAll(falsePositives.get(keyType).getIgnoredKeysInAllAPIs());
                ignoreData.getIgnoredKeysInSelectedAPIs().putAll(falsePositives.get(keyType).getIgnoredKeysInSelectedAPIs());
                AktoDataTypeDao.instance.updateOne(Filters.eq(CustomDataType.NAME, keyType),
                    Updates.combine(
                            Updates.set(CustomDataType.IGNORE_DATA, ignoreData),
                            Updates.set(CustomDataType.TIMESTAMP, Context.now())));
            }
        }

        executorService.schedule( new Runnable() {
            public void run() {
                Context.accountId.set(accountId);
                for (String keyType : falsePositives.keySet()) {
                    IgnoreData ignoreData = falsePositives.get(keyType);
                    for (String key : ignoreData.getIgnoredKeysInSelectedAPIs().keySet()) {
                        for (ParamId paramId : ignoreData.getIgnoredKeysInSelectedAPIs().get(key)) {
                            SingleTypeInfo sti = new SingleTypeInfo(paramId, null, null, accountId, accountId,
                                    accountId, null, null, accountId, accountId);
                                    sti.setSubType(createSubType(keyType));
                            SingleTypeInfoDao.instance.getMCollection().deleteMany(
                                    SingleTypeInfoDao.createFilters(sti));
                            SingleTypeInfoDao.instance.updateMany(
                                    SingleTypeInfoDao.createFiltersWithoutSubType(sti),
                                    Updates.set(SingleTypeInfo.SUB_TYPE,createSubType(keyType).getName()));
                        }
                    }
                    for (String key : ignoreData.getIgnoredKeysInAllAPIs()) {
                        SingleTypeInfoDao.instance.getMCollection().deleteMany(
                            Filters.and(Filters.eq(SingleTypeInfo._PARAM, key),
                                Filters.eq(SingleTypeInfo.SUB_TYPE,createSubType(keyType).getName())));
                        SingleTypeInfoDao.instance.updateMany(
                            Filters.eq(SingleTypeInfo._PARAM, key),
                            Updates.set(SingleTypeInfo.SUB_TYPE, createSubType(keyType).getName()));
                    }
                }
            }
        }, 1 , TimeUnit.SECONDS);

        return Action.SUCCESS.toUpperCase();
    }

    public Map<String, IgnoreData> getFalsePositives() {
        return falsePositives;
    }

    public void setFalsePositives(Map<String, IgnoreData> falsePositives) {
        this.falsePositives = falsePositives;
    }
}