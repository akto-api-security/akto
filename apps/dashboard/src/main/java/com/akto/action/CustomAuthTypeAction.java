package com.akto.action;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dao.CustomAuthTypeDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.CustomAuthType;
import com.akto.dto.data_types.Conditions;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import com.akto.dto.User;

public class CustomAuthTypeAction extends UserAction{
    private String name;
    private List<String> keys;
    private Conditions.Operator operator;
    private boolean active;
    private List<CustomAuthType> customAuthTypes;
    private Map<Integer,String> usersMap;
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
        CustomAuthType customAuthType = CustomAuthTypeDao.instance.findOne("name",name);
        if(customAuthType!=null){
            addActionError("Auth type name needs to be unique");
            return ERROR.toUpperCase();
        } else {
            active = true;
            customAuthType = new CustomAuthType(name, keys, operator, active,user.getId());
            CustomAuthTypeDao.instance.insertOne(customAuthType);
        }
        fetchCustomAuthTypes();
        return Action.SUCCESS.toUpperCase();
    }

    public String updateCustomAuthType(){
        User user = getSUser();
        CustomAuthType customAuthType = CustomAuthTypeDao.instance.findOne("name",name);
        if(customAuthType==null){
            addActionError("Custom Auth Type does not exist");
            return ERROR.toUpperCase();
        } else if(user.getId()!=customAuthType.getCreatorId()){
            addActionError("Unautherized Request");
            return ERROR.toUpperCase();
        } else {
            CustomAuthTypeDao.instance.updateOne(Filters.eq("name", name),
                    Updates.combine(
                        Updates.set("active", active), 
                        Updates.set("keys", keys),
                        Updates.set("operator", operator), 
                        Updates.set("name", name),
                        Updates.set("timestamp", Context.now())));
        }
        fetchCustomAuthTypes();
        return Action.SUCCESS.toUpperCase();
    }

    public String updateCustomAuthTypeStatus(){
        User user = getSUser();
        CustomAuthType customAuthType = CustomAuthTypeDao.instance.findOne("name",name);
        if(customAuthType==null){
            addActionError("Custom Auth Type does not exist");
            return ERROR.toUpperCase();
        } else if(user.getId()!=customAuthType.getCreatorId()){
            addActionError("Unautherized Request");
            return ERROR.toUpperCase();
        }  else {
            CustomAuthTypeDao.instance.updateOne(Filters.eq("name", name),
                    Updates.combine(
                        Updates.set("active", active),
                        Updates.set("timestamp",Context.now())));
        }
        fetchCustomAuthTypes();
        return Action.SUCCESS.toUpperCase();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setKeys(List<String> keys) {
        this.keys = keys;
    }

    public void setOperator(Conditions.Operator operator) {
        this.operator = operator;
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
}