package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.dao.notifications.CustomWebhooksDao;
import com.akto.dao.notifications.CustomWebhooksResultDao;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.dto.notifications.CustomWebhook.ActiveStatus;
import com.akto.dto.notifications.CustomWebhook.WebhookType;
import com.akto.dto.notifications.CustomWebhookResult;
import com.akto.dto.type.KeyTypes;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.listener.InitializerListener;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

import org.apache.struts2.interceptor.ServletRequestAware;
import org.bson.conversions.Bson;

import static com.akto.utils.Utils.createDashboardUrlFromRequest;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

public class WebhookAction extends UserAction implements ServletRequestAware{
    
    private int id;
    private String webhookName;
    private String url;
    private String headerString;
    private String queryParams;
    private String body;
    private Method method;
    private int frequencyInSeconds;
    private ActiveStatus activeStatus;
    private CustomWebhookResult customWebhookResult;
    private List<CustomWebhook.WebhookOptions> selectedWebhookOptions;
    private List<CustomWebhook> customWebhooks;
    private List<String> newEndpointCollections;
    private List<String> newSensitiveEndpointCollections;
    private int batchSize;
    private boolean sendInstantly;
    private String webhookType;
    private boolean webhookPresent;
    private String webhookOption;
    private String dashboardUrl;

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private int customWebhookId;

    public String fetchCustomWebhooks() {
        if (customWebhookId == 0) {
            customWebhooks = CustomWebhooksDao.instance.findAll(new BasicDBObject());
        } else {
            customWebhooks = CustomWebhooksDao.instance.findAll(Filters.eq("_id", customWebhookId));
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchLatestWebhookResult(){
        customWebhookResult = CustomWebhooksResultDao.instance.findLatestOne(Filters.eq("webhookId", id));
        return Action.SUCCESS.toUpperCase();
    }

    public String addCustomWebhook(){
        activeStatus = ActiveStatus.ACTIVE;

        WebhookType type = WebhookType.DEFAULT;
        try {
            type = WebhookType.valueOf(webhookType);
        } catch (Exception e) {
        }
        boolean isUrl = type.equals(WebhookType.GMAIL) ||  KeyTypes.patternToSubType.get(SingleTypeInfo.URL).matcher(url).matches() ;
        
        try{
            OriginalHttpRequest.buildHeadersMap(headerString);
        }
        catch(Exception e){
            addActionError("Please enter valid headers");
            return ERROR.toUpperCase();
        }
        if (selectedWebhookOptions == null && body == null) {
            addActionError("Please select at least one option");
            return ERROR.toUpperCase();
        }

        if (!isUrl) {
            addActionError("Please enter a valid url");
            return ERROR.toUpperCase();
        } else if (frequencyInSeconds<=0){
            addActionError("Please enter a valid frequency");
            return ERROR.toUpperCase();
        } else {
            int now = Context.now();
            String userEmail = getSUser().getLogin();
            if (userEmail == null) return ERROR.toUpperCase();
            CustomWebhook customWebhook = new CustomWebhook(now,webhookName,url,headerString,queryParams,body,method,frequencyInSeconds,userEmail,now,now,0,activeStatus, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections);
            if (batchSize > 0) {
                customWebhook.setBatchSize(batchSize);
            }
            customWebhook.setSendInstantly(sendInstantly);
            customWebhook.setWebhookType(type);
            customWebhook.setDashboardUrl(this.dashboardUrl);
            CustomWebhooksDao.instance.insertOne(customWebhook);
            fetchCustomWebhooks();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String updateCustomWebhook(){
        activeStatus = ActiveStatus.ACTIVE;
        String userEmail = getSUser().getLogin();
        if (userEmail == null) return ERROR.toUpperCase();

        CustomWebhook customWebhook = CustomWebhooksDao.instance.findOne(
            Filters.eq("_id",id)
        );

        if (customWebhook == null){
            addActionError("The webhook does not exist");
            return ERROR.toUpperCase();
        } else if ( !userEmail.equals(customWebhook.getUserEmail())){
            addActionError("Unauthorized Request");
            return ERROR.toUpperCase();
        } 
        boolean isUrl = customWebhook.getWebhookType().equals(WebhookType.GMAIL) ||  KeyTypes.patternToSubType.get(SingleTypeInfo.URL).matcher(url).matches() ;

        try{
            OriginalHttpRequest.buildHeadersMap(headerString);
        }
        catch(Exception e){
            addActionError("Please enter valid headers");
            return ERROR.toUpperCase();
        }

        if (!isUrl){
            addActionError("Please enter a valid url");
            return ERROR.toUpperCase();
        } else if (frequencyInSeconds<=0){
            addActionError("Please enter a valid frequency");
            return ERROR.toUpperCase();
        } else if (selectedWebhookOptions == null && body == null) {
            addActionError("Please select at least one option");
            return ERROR.toUpperCase();
        } else {
            int now = Context.now();

            Bson updates =
            Updates.combine(
                Updates.set("url", url),
                Updates.set("headerString", headerString),
                Updates.set("body", body),
                Updates.set("queryParams",queryParams),
                Updates.set("method", method),
                Updates.set("frequencyInSeconds", frequencyInSeconds),
                Updates.set("lastUpdateTime",now),
                Updates.set("webhookName", webhookName),
                Updates.set(CustomWebhook.SELECTED_WEBHOOK_OPTIONS, selectedWebhookOptions),
                Updates.set(CustomWebhook.NEW_ENDPOINT_COLLECTIONS, newEndpointCollections),
                Updates.set(CustomWebhook.NEW_SENSITIVE_ENDPOINT_COLLECTIONS, newSensitiveEndpointCollections),
                Updates.set(CustomWebhook.SEND_INSTANTLY, sendInstantly),
                Updates.set(CustomWebhook.DASHBOARD_URL, this.dashboardUrl)
            );

            if (batchSize > 0) {
                updates = Updates.combine(updates, Updates.set(CustomWebhook.BATCH_SIZE, batchSize));
            }

            CustomWebhooksDao.instance.updateOne(Filters.eq("_id",id), updates);
            fetchCustomWebhooks();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String changeStatus(){
        CustomWebhook customWebhook = CustomWebhooksDao.instance.findOne(
            Filters.eq("_id",id)
        );

        String userEmail = getSUser().getLogin();
        if (userEmail == null) return ERROR.toUpperCase();

        if (customWebhook == null){
            addActionError("The webhook does not exist");
            return ERROR.toUpperCase();
        } else if ( !userEmail.equals(customWebhook.getUserEmail() )){
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

    public String runOnce(){
        CustomWebhook customWebhook = CustomWebhooksDao.instance.findOne(
            Filters.eq("_id",id)
        );

        String userEmail = getSUser().getLogin();
        if (userEmail == null) return ERROR.toUpperCase();

        if (customWebhook == null){
            addActionError("The webhook does not exist");
            return ERROR.toUpperCase();
        } else if ( !userEmail.equals(customWebhook.getUserEmail() )){
            addActionError("Unauthorized Request");
            return ERROR.toUpperCase();
        } else{

            int accountId = Context.accountId.get();
            
            executorService.schedule( new Runnable() {
                public void run() {
                    Context.accountId.set(accountId);
                    customWebhook.setFrequencyInSeconds(0);
                    customWebhook.setLastSentTimestamp(0);
                    customWebhook.setActiveStatus(ActiveStatus.ACTIVE);
                    InitializerListener.webhookSenderUtil(customWebhook);
                }
            }, 1 , TimeUnit.SECONDS);
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String deleteCustomWebhook() {

        CustomWebhook customWebhook = CustomWebhooksDao.instance.findOne(
            Filters.eq("_id",id)
        );

        String userEmail = getSUser().getLogin();
        if (userEmail == null) return ERROR.toUpperCase();

        if (!userEmail.equals(customWebhook.getUserEmail())){
            addActionError("Unauthorized Request");
            return ERROR.toUpperCase();
        } else {
            CustomWebhooksDao.instance.deleteAll(new BasicDBObject("_id", id));
        }
        return Action.SUCCESS.toUpperCase();
    }

    private enum ValidationDataType{
        TYPE, OPTION
    }

    private boolean validationCheck(String data, ValidationDataType expectedType) {
        if (data == null || data.isEmpty()) {
            addActionError("webhook " + expectedType + " is invalid");
            return false;
        }
        try {
            switch (expectedType) {
                case TYPE:
                    CustomWebhook.WebhookType.valueOf(data);
                    break;
                case OPTION:
                    CustomWebhook.WebhookOptions.valueOf(data);
                    break;
                default:
                    throw new Exception("Invalid " + expectedType);
            }
        } catch (Exception e) {
            addActionError("webhook " + expectedType + " is invalid");
            return false;
        }
        return true;
    }

    public String checkWebhook() {

        if (!(validationCheck(this.webhookType, ValidationDataType.TYPE) &&
                validationCheck(this.webhookOption, ValidationDataType.OPTION))) {
            return ERROR.toUpperCase();
        }

        CustomWebhook webhook = CustomWebhooksDao.instance.findOne(
                Filters.and(
                        Filters.eq(CustomWebhook.WEBHOOK_TYPE, webhookType),
                        Filters.in(CustomWebhook.SELECTED_WEBHOOK_OPTIONS, webhookOption)));

        if (webhook != null) {
            webhookPresent = true;
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

    public String getHeaderString() {
        return headerString;
    }

    public void setHeaderString(String headerString) {
        this.headerString = headerString;
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

    public CustomWebhookResult getCustomWebhookResult() {
        return customWebhookResult;
    }

    public List<CustomWebhook> getCustomWebhooks() {
        return this.customWebhooks;
    }

    public List<CustomWebhook.WebhookOptions> getSelectedWebhookOptions() {
        return selectedWebhookOptions;
    }

    public void setSelectedWebhookOptions(List<CustomWebhook.WebhookOptions> selectedWebhookOptions) {
        this.selectedWebhookOptions = selectedWebhookOptions;
    }

    public List<String> getNewEndpointCollections() {
        return newEndpointCollections;
    }

    public void setNewEndpointCollections(List<String> newEndpointCollections) {
        this.newEndpointCollections = newEndpointCollections;
    }

    public List<String> getNewSensitiveEndpointCollections() {
        return newSensitiveEndpointCollections;
    }

    public void setNewSensitiveEndpointCollections(List<String> newSensitiveEndpointCollections) {
        this.newSensitiveEndpointCollections = newSensitiveEndpointCollections;
    }

    public void setCustomWebhookId(int customWebhookId) {
        this.customWebhookId = customWebhookId;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getWebhookType() {
        return webhookType;
    }

    public void setWebhookType(String webhookType) {
        this.webhookType = webhookType;
    }

    public boolean getSendInstantly() {
        return sendInstantly;
    }

    public void setSendInstantly(boolean sendInstantly) {
        this.sendInstantly = sendInstantly;
    }

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.dashboardUrl = createDashboardUrlFromRequest(request);
    }

    public void setWebhookOption(String webhookOption) {
        this.webhookOption = webhookOption;
    }

    public boolean getWebhookPresent() {
        return webhookPresent;
    }
}