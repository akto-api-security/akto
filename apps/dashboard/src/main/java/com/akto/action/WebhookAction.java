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
    private String webhookName;
    private String url;
    private Map<String, List<String>> headers = new HashMap<>();
    private String queryParams;
    private String body;
    private Method method;
    private int frequencyInSeconds;
    private ActiveStatus activeStatus;
    BasicDBObject response = new BasicDBObject();

    public String getLastSentResult(){

        String userEmail = null;
        try{
            userEmail = getSUser().getLogin();
        } catch(Exception e){
            e.printStackTrace();
        }

        MongoCursor<CustomWebhookResult> webhookResultCursor = CustomWebhooksResultDao.instance.getMCollection()
                .find(Filters.eq("userEmail", userEmail))
                .sort(Sorts.descending("timestamp"))
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
            String userEmail = null;
            try{
                userEmail = getSUser().getLogin();
            } catch(Exception e){
                e.printStackTrace();
            }
            CustomWebhook customWebhook = new CustomWebhook(now,webhookName,url,headers,queryParams,body,method,frequencyInSeconds,userEmail,now,now,now,activeStatus);
            CustomWebhooksDao.instance.insertOne(customWebhook);
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String updateCustomWebhook(){

        CustomWebhook customWebhook = CustomWebhooksDao.instance.findOne(
            Filters.eq("_id",id)
        );
        boolean isUrl = KeyTypes.patternToSubType.get(SingleTypeInfo.URL).matcher(url).matches();

        String userEmail = null;
        try{
            userEmail = getSUser().getLogin();
        } catch(Exception e){
            e.printStackTrace();
        }

        if (customWebhook == null){
            addActionError("The webhook does not exist");
            return ERROR.toUpperCase();
        } else if (customWebhook.getUserEmail()!=userEmail){
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
                Updates.set("queryParams",queryParams),
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

        String userEmail = null;
        try{
            userEmail = getSUser().getLogin();
        } catch(Exception e){
            e.printStackTrace();
        }

        if (customWebhook == null){
            addActionError("The webhook does not exist");
            return ERROR.toUpperCase();
        } else if (customWebhook.getUserEmail()!=userEmail){
            addActionError("Unauthorized Request");
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

        CustomWebhook customWebhook = CustomWebhooksDao.instance.findOne(
            Filters.eq("_id",id)
        );

        String userEmail = null;
        try{
            userEmail = getSUser().getLogin();
        } catch(Exception e){
            e.printStackTrace();
        }

        if (customWebhook.getUserEmail()!=userEmail){
            addActionError("Unauthorized Request");
            return ERROR.toUpperCase();
        } else {
            CustomWebhooksDao.instance.deleteAll(new BasicDBObject("_id", id));
        }
        return Action.SUCCESS.toUpperCase();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getWebhookName() {
        return webhookName;
    }

    public void setWebhookName(String webhookName) {
        this.webhookName = webhookName;
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

    public String getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(String queryParams) {
        this.queryParams = queryParams;
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

    public BasicDBObject getResponse() {
        return response;
    }

    public void setResponse(BasicDBObject response) {
        this.response = response;
    }
}