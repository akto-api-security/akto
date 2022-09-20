package com.akto.action;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;

import com.akto.dao.context.Context;
import com.akto.dao.notifications.CustomWebhooksDao;
import com.akto.dao.notifications.CustomWebhooksResultDao;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.dto.notifications.CustomWebhookResult;
import com.akto.dto.notifications.CustomWebhook.ActiveStatus;
import com.akto.dto.type.KeyTypes;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

public class WebhookAction extends UserAction {
    
    private int id;
    String url;
    Map<String, List<String>> headers = new HashMap<>();
    String body;
    Method method;
    int frequencyInSeconds;
    ActiveStatus activeStatus;
    String error;
    BasicDBObject response = new BasicDBObject();

    public String getLastSentResult(){

        String userEmail = getSUser().getLogin();

        MongoCursor<CustomWebhookResult> webhookResultCursor = CustomWebhooksResultDao.instance.getMCollection()
                                                                .find(Filters.eq("userEmail",userEmail))
                                                                .sort(Sorts.descending("id"))
                                                                .limit(1).cursor();

        if(webhookResultCursor.hasNext()){
            CustomWebhookResult customWebhookResult = webhookResultCursor.next();
            response.put("lastSentResult",customWebhookResult);
        }

        return Action.SUCCESS.toUpperCase();
    }


    public String addCustomWebhook(){

        boolean isUrl = KeyTypes.patternToSubType.get(SingleTypeInfo.URL).matcher(url).matches();

        if (!isUrl) {
            addActionError("Please enter a valid url");
            return ERROR.toUpperCase();
        } else if (frequencyInSeconds<=0){
            addActionError("Please enter a valid frequency");
            return ERROR.toUpperCase();
        } else {
            int now = Context.now();
            String userEmail = getSUser().getLogin();
            CustomWebhook customWebhook = new CustomWebhook(now,url,headers,body,method,frequencyInSeconds,userEmail,now,now,now,activeStatus);
            CustomWebhooksDao.instance.insertOne(customWebhook);
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String updateCustomWebhook(){

        CustomWebhook customWebhook = CustomWebhooksDao.instance.findOne(
            Filters.eq("_id",id)
        );
        boolean isUrl = KeyTypes.patternToSubType.get(SingleTypeInfo.URL).matcher(url).matches();

        if (customWebhook == null){
            addActionError("The webhook does not exist");
            return ERROR.toUpperCase();
        } else if (customWebhook.getUserEmail()!=getSUser().getLogin()){
            addActionError("Unauthorized Request");
            return ERROR.toUpperCase();
        } else if (!isUrl){
            addActionError("Please enter a valid url");
            return ERROR.toUpperCase();
        } else if (frequencyInSeconds<=0){
            addActionError("Please enter a valid frequency");
            return ERROR.toUpperCase();
        } else {
            int now = Context.now();

            Bson updates = 
            Updates.combine(
                Updates.set("url", url),
                Updates.set("headers", headers),
                Updates.set("body", body),
                Updates.set("method", method),
                Updates.set("frequencyInSeconds", frequencyInSeconds),
                Updates.set("lastUpdateTime",now)
            );

            CustomWebhooksDao.instance.updateOne(Filters.eq("_id",id), updates);
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String changeStatus(){
        CustomWebhook customWebhook = CustomWebhooksDao.instance.findOne(
            Filters.eq("_id",id)
        );

        if (customWebhook == null){
            addActionError("The webhook does not exist");
            return ERROR.toUpperCase();
        } else{
            int now = Context.now();
            
            Bson updates = 
            Updates.combine(
                Updates.set("activeStatus",activeStatus),
                Updates.set("lastUpdateTime",now)
            );

            CustomWebhooksDao.instance.updateOne(Filters.eq("_id",id), updates);
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String deleteCustomWebhook() {
        CustomWebhooksDao.instance.deleteAll(new BasicDBObject("_id", id));
        return Action.SUCCESS.toUpperCase();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, List<String>> headers) {
        this.headers = headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public int getFrequencyInSeconds() {
        return frequencyInSeconds;
    }

    public void setFrequencyInSeconds(int frequencyInSeconds) {
        this.frequencyInSeconds = frequencyInSeconds;
    }

    public ActiveStatus getActiveStatus() {
        return activeStatus;
    }

    public void setActiveStatus(ActiveStatus activeStatus) {
        this.activeStatus = activeStatus;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public BasicDBObject getResponse() {
        return this.response;
    }

    public void setResponse(BasicDBObject response) {
        this.response = response;
    }
}