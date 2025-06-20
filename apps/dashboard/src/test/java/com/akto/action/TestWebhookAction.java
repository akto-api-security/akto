package com.akto.action;

import static org.junit.Assert.assertEquals;

import java.util.*;

import com.akto.utils.Utils;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.notifications.CustomWebhooksDao;
import com.akto.dao.notifications.CustomWebhooksResultDao;
import com.akto.dto.User;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.dto.notifications.CustomWebhookResult;
import com.akto.dto.notifications.CustomWebhook.ActiveStatus;

import com.akto.dto.type.URLMethods.Method;
import com.mongodb.BasicDBObject;

public class TestWebhookAction extends MongoBasedTest{


    @Test
    public void testFetchCustomWebhooks(){
        CustomWebhooksDao.instance.getMCollection().drop();
        CustomWebhook customWebhook = new CustomWebhook(1,"webhook name","http://test.akto.io","","queryParam=test","body",Method.POST,10,"test@akto.io",0,0,0,ActiveStatus.ACTIVE, null, null, null);
        CustomWebhook customWebhook2 = new CustomWebhook(2,"webhook name","http://test.akto.io","","queryParam=test","body",Method.POST,10,"test@akto.io",0,0,0,ActiveStatus.ACTIVE, null, null, null);
        CustomWebhooksDao.instance.insertOne(customWebhook);
        CustomWebhooksDao.instance.insertOne(customWebhook2);

        WebhookAction webhookAction = new WebhookAction();

        Map<String,Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("test@akto.io");
        session.put("user",user);
        webhookAction.setSession(session);
        
        String result = webhookAction.fetchCustomWebhooks();
        assertEquals("SUCCESS",result);
        List<CustomWebhook> customWebhooks = CustomWebhooksDao.instance.findAll(new BasicDBObject());
        assertEquals(2,customWebhooks.size());
    }

    @Test
    public void testFetchLatestWebhookResult(){
        CustomWebhooksResultDao.instance.getMCollection().drop();
        List<CustomWebhookResult> customWebhookResults = new ArrayList<>();
        customWebhookResults.add (new CustomWebhookResult(1000,"test@akto.io",1,"message",new ArrayList<>()));
        customWebhookResults.add (new CustomWebhookResult(1000,"test@akto.io",2,"message",new ArrayList<>()));
        customWebhookResults.add (new CustomWebhookResult(100,"test@akto.io",3,"message",new ArrayList<>()));
        CustomWebhooksResultDao.instance.insertMany(customWebhookResults);

        WebhookAction webhookAction = new WebhookAction();
        webhookAction.setId(1000);

        Map<String,Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("test@akto.io");
        session.put("user",user);
        webhookAction.setSession(session);
        
        String result = webhookAction.fetchLatestWebhookResult();
        assertEquals("SUCCESS", result);
        assertEquals(2,webhookAction.getCustomWebhookResult().getTimestamp());
    }

    @Test
    public void testAddCustomWebhook(){
        CustomWebhooksDao.instance.getMCollection().drop();
        CustomWebhook customWebhook = new CustomWebhook(1,"webhook name","http://test.akto.io","","queryParam=test","body",Method.POST,10,"test@akto.io",0,0,0,ActiveStatus.ACTIVE, Collections.singletonList(CustomWebhook.WebhookOptions.NEW_ENDPOINT), null, null);
        CustomWebhooksDao.instance.insertOne(customWebhook);
        
        WebhookAction webhookAction = new WebhookAction();
        webhookAction.setWebhookName("webhook name");
        webhookAction.setUrl("http://test.akto.io");
        webhookAction.setHeaderString("");
        webhookAction.setQueryParams("queryParam=test");
        webhookAction.setBody("Sample body");
        webhookAction.setMethod(Method.POST);
        webhookAction.setFrequencyInSeconds(10);
        webhookAction.setActiveStatus(ActiveStatus.ACTIVE);
        webhookAction.setSelectedWebhookOptions(Collections.singletonList(CustomWebhook.WebhookOptions.NEW_ENDPOINT));

        Map<String,Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("test@akto.io");
        session.put("user",user);
        webhookAction.setSession(session);
        
        String result = webhookAction.addCustomWebhook();
        assertEquals("SUCCESS",result);
        assertEquals(2, webhookAction.getCustomWebhooks().size());
    }
    
    @Test
    public void testUpdateCustomWebhook(){
        CustomWebhooksDao.instance.getMCollection().drop();
        CustomWebhook customWebhook = new CustomWebhook(1,"webhook name","http://test.akto.io","","queryParam=test","body",Method.POST,10,"test@akto.io",0,0,0,ActiveStatus.ACTIVE, Collections.singletonList(CustomWebhook.WebhookOptions.NEW_ENDPOINT), null, null);
        CustomWebhooksDao.instance.insertOne(customWebhook);

        WebhookAction webhookAction = new WebhookAction();
        webhookAction.setId(1);
        webhookAction.setWebhookName("new webhook name");
        webhookAction.setUrl("http://test.akto.io");
        webhookAction.setHeaderString("");
        webhookAction.setQueryParams("newqueryParam=test");
        webhookAction.setBody("New Sample body");
        webhookAction.setMethod(Method.POST);
        webhookAction.setFrequencyInSeconds(20);
        webhookAction.setActiveStatus(ActiveStatus.ACTIVE);
        webhookAction.setSelectedWebhookOptions(Collections.singletonList(CustomWebhook.WebhookOptions.NEW_ENDPOINT));

        Map<String,Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("test@akto.io");
        session.put("user",user);
        webhookAction.setSession(session);
        
        String result = webhookAction.updateCustomWebhook();
        assertEquals("SUCCESS", result);
        assertEquals("new webhook name",webhookAction.getCustomWebhooks().get(0).getWebhookName());
    }

    @Test
    public void testChangeStatus(){
        CustomWebhooksDao.instance.getMCollection().drop();
        List<CustomWebhook.WebhookOptions> selectedWebhookOptions = new ArrayList<>();
        selectedWebhookOptions.add(CustomWebhook.WebhookOptions.API_THREAT_PAYLOADS);
        CustomWebhook customWebhook = new CustomWebhook(1,"webhook name","http://test.akto.io","","queryParam=test","body",Method.POST,10,"test@akto.io",0,0,0,ActiveStatus.ACTIVE, selectedWebhookOptions, null, null);
        CustomWebhooksDao.instance.insertOne(customWebhook);

        WebhookAction webhookAction = new WebhookAction();
        webhookAction.setId(1);
        webhookAction.setActiveStatus(ActiveStatus.INACTIVE);
        
        Map<String,Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("test@akto.io");
        session.put("user",user);
        webhookAction.setSession(session);

        String result = webhookAction.changeStatus();
        assertEquals("SUCCESS",result);
    }

    @Test
    public void testDeleteCustomWebhook(){
        CustomWebhooksDao.instance.getMCollection().drop();
        CustomWebhook customWebhook = new CustomWebhook(1,"webhook name","http://test.akto.io","","queryParam=test","body",Method.POST,10,"test@akto.io",0,0,0,ActiveStatus.ACTIVE, null, null, null);
        CustomWebhook customWebhook2 = new CustomWebhook(2,"webhook name","http://test.akto.io","","queryParam=test","body",Method.POST,10,"test@akto.io",0,0,0,ActiveStatus.ACTIVE, null, null, null);
        CustomWebhooksDao.instance.insertOne(customWebhook);
        CustomWebhooksDao.instance.insertOne(customWebhook2);

        WebhookAction webhookAction = new WebhookAction();
        webhookAction.setId(1);
        Map<String,Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("test@akto.io");
        session.put("user",user);
        webhookAction.setSession(session);
        String result = webhookAction.deleteCustomWebhook();
        assertEquals("SUCCESS",result);
        List<CustomWebhook> customWebhooks = CustomWebhooksDao.instance.findAll(new BasicDBObject());
        assertEquals(1,customWebhooks.size());
    }

    @Test
    public void testValidURL() {
        System.out.println(Utils.isValidURL("asdad"));
    }

    @Test
    public void testValidationCheck(){
        WebhookAction webhookAction = new WebhookAction();
        webhookAction.setWebhookType(CustomWebhook.WebhookType.MICROSOFT_TEAMS.name());
        webhookAction.setWebhookOption(CustomWebhook.WebhookOptions.TESTING_RUN_RESULTS.name());
        assertEquals("SUCCESS", webhookAction.checkWebhook());

        webhookAction.setWebhookType(CustomWebhook.WebhookType.MICROSOFT_TEAMS.name()+"1");
        assertEquals("ERROR", webhookAction.checkWebhook());
        webhookAction.setWebhookOption(CustomWebhook.WebhookOptions.TESTING_RUN_RESULTS.name() + "1");
        webhookAction.setWebhookType(CustomWebhook.WebhookType.MICROSOFT_TEAMS.name());
        assertEquals("ERROR", webhookAction.checkWebhook());

    }
}
