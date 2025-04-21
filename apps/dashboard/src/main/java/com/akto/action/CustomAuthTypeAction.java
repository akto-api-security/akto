package com.akto.action;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.dao.CustomAuthTypeDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.CustomAuthType;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import com.akto.dto.User;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.utils.CustomAuthUtil;

public class CustomAuthTypeAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(CustomAuthTypeAction.class, LogDb.DASHBOARD);

    private String name;
    private List<String> headerKeys;
    private List<String> payloadKeys;
    private boolean active;
    private List<CustomAuthType> customAuthTypes;
    private Map<Integer,String> usersMap;
    private CustomAuthType customAuthType;

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public String fetchCustomAuthTypes(){
        customAuthTypes = CustomAuthTypeDao.instance.findAll(new BasicDBObject());
        Set<Integer> userIds = new HashSet<>();
        for (CustomAuthType customAuthType: customAuthTypes) {
            userIds.add(customAuthType.getCreatorId());
        }
        usersMap = UsersDao.instance.getUsernames(userIds);
        return Action.SUCCESS.toUpperCase();
    }

    public String addCustomAuthType(){
        User user = getSUser();
        customAuthType = CustomAuthTypeDao.instance.findOne(CustomAuthType.NAME,name);
        if(customAuthType!=null){
            addActionError("Auth type name needs to be unique");
            return ERROR.toUpperCase();
        } else {
            active = true;
            customAuthType = new CustomAuthType(name, headerKeys, payloadKeys, active,user.getId(), null, null);
            CustomAuthTypeDao.instance.insertOne(customAuthType);
        }
        fetchCustomAuthTypes();

        int accountId = Context.accountId.get();
        SingleTypeInfo.fetchCustomAuthTypes(accountId);
        executorService.schedule( new Runnable() {
            public void run() {
                Context.accountId.set(accountId);
                List<CustomAuthType> customAuthTypes = SingleTypeInfo.getCustomAuthType(accountId);
                CustomAuthUtil.customAuthTypeUtil(customAuthTypes);
            }
        }, 5 , TimeUnit.SECONDS);
        return Action.SUCCESS.toUpperCase();
    }

    public String updateCustomAuthType(){
        customAuthType = CustomAuthTypeDao.instance.findOne(CustomAuthType.NAME,name);
        if(customAuthType==null){
            addActionError("Custom Auth Type does not exist");
            return ERROR.toUpperCase();
        } else {
            CustomAuthTypeDao.instance.updateOne(Filters.eq(CustomAuthType.NAME, name),
                    Updates.combine(
                        Updates.set(CustomAuthType.ACTIVE, active), 
                        Updates.set("headerKeys", headerKeys),
                        Updates.set("payloadKeys", payloadKeys),
                        Updates.set(CustomAuthType.NAME, name),
                        Updates.set("timestamp", Context.now())));
        }
        fetchCustomAuthTypes();
        CustomAuthUtil.resetAllCustomAuthTypes();
        int accountId = Context.accountId.get();
        SingleTypeInfo.fetchCustomAuthTypes(accountId);
        customAuthType = CustomAuthTypeDao.instance.findOne(CustomAuthType.NAME,name);
        executorService.schedule(() -> {
            Context.accountId.set(accountId);
            CustomAuthUtil.customAuthTypeUtil(SingleTypeInfo.getCustomAuthType(accountId));
        }, 5 , TimeUnit.SECONDS);
        return Action.SUCCESS.toUpperCase();
    }

    public String updateCustomAuthTypeStatus(){
        customAuthType = CustomAuthTypeDao.instance.findOne(CustomAuthType.NAME,name);
        if(customAuthType==null){
            addActionError("Custom Auth Type does not exist");
            return ERROR.toUpperCase();
        } else {
            CustomAuthTypeDao.instance.updateOne(Filters.eq(CustomAuthType.NAME, name),
                    Updates.combine(
                        Updates.set(CustomAuthType.ACTIVE, active),
                        Updates.set("timestamp",Context.now())));
        }
        int accountId = Context.accountId.get();
        fetchCustomAuthTypes();
        SingleTypeInfo.fetchCustomAuthTypes(accountId);
        customAuthType = CustomAuthTypeDao.instance.findOne(CustomAuthType.NAME,name);
        return Action.SUCCESS.toUpperCase();
    }

    public String resetAllCustomAuthTypes() {
        try {
            CustomAuthUtil.resetAllCustomAuthTypes();
            int accountId = Context.accountId.get();
            SingleTypeInfo.fetchCustomAuthTypes(accountId);
            executorService.schedule( new Runnable() {
                public void run() {
                    Context.accountId.set(accountId);
                    CustomAuthUtil.customAuthTypeUtil(SingleTypeInfo.getCustomAuthType(accountId));
                }
            }, 5 , TimeUnit.SECONDS);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "ERROR: Reset custom auth types - " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setHeaderKeys(List<String> headerKeys) {
        this.headerKeys = headerKeys;
    }

    public void setPayloadKeys(List<String> payloadKeys) {
        this.payloadKeys = payloadKeys;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<CustomAuthType> getCustomAuthTypes() {
        return customAuthTypes;
    }

    public Map<Integer, String> getUsersMap() {
        return usersMap;
    }
    public CustomAuthType getCustomAuthType() {
        return customAuthType;
    }
}