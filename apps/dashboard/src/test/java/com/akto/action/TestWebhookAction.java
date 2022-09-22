package com.akto.action;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.notifications.CustomWebhooksDao;
import com.akto.dao.notifications.CustomWebhooksResultDao;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.dto.notifications.CustomWebhookResult;
import com.akto.dto.notifications.CustomWebhook.ActiveStatus;

import com.akto.dto.type.URLMethods.Method;
import com.mongodb.BasicDBObject;

public class TestWebhookAction extends MongoBasedTest{


    @Test
    public void testGetSentLastResult(){
        CustomWebhooksResultDao.instance.getMCollection().drop();
        List<CustomWebhookResult> customWebhookResults = new ArrayList<>();
        customWebhookResults.add (new CustomWebhookResult(1,10,null,1,"message",new ArrayList<>()));
        customWebhookResults.add (new CustomWebhookResult(2,100,null,2,"message",new ArrayList<>()));
        customWebhookResults.add (new CustomWebhookResult(3,1000,null,3,"message",new ArrayList<>()));
        CustomWebhooksResultDao.instance.insertMany(customWebhookResults);

        WebhookAction webhookAction = new WebhookAction();
        String result = webhookAction.fetchLatestWebhookResult();
        assertEquals("SUCCESS", result);
    }

    @Test
    public void testAddCustomWebhook(){
        CustomWebhooksDao.instance.getMCollection().drop();
        
        WebhookAction webhookAction = new WebhookAction();
        webhookAction.setWebhookName("webhook name");
        webhookAction.setUrl("http://test.akto.io");
        webhookAction.setHeaderString("");
        webhookAction.setQueryParams("queryParam=test");
        webhookAction.setBody("Sample body");
        webhookAction.setMethod(Method.POST);
        webhookAction.setFrequencyInSeconds(10);
        webhookAction.setActiveStatus(ActiveStatus.ACTIVE);

        String result = webhookAction.addCustomWebhook();
        assertEquals("SUCCESS",result);
    }
    
    @Test
    public void testUpdateCustomWebhook(){
        CustomWebhooksDao.instance.getMCollection().drop();
        CustomWebhook customWebhook = new CustomWebhook(1,"webhook name","http://test.akto.io","","queryParam=test","body",Method.POST,10,null,0,0,0,ActiveStatus.ACTIVE);
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

        String result = webhookAction.updateCustomWebhook();
        assertEquals("SUCCESS", result);
    }

    @Test
    public void testChangeStatus(){
        CustomWebhooksDao.instance.getMCollection().drop();
        CustomWebhook customWebhook = new CustomWebhook(1,"webhook name","http://test.akto.io","","queryParam=test","body",Method.POST,10,null,0,0,0,ActiveStatus.ACTIVE);
        CustomWebhooksDao.instance.insertOne(customWebhook);

        WebhookAction webhookAction = new WebhookAction();
        webhookAction.setId(1);
        webhookAction.setActiveStatus(ActiveStatus.INACTIVE);

        String result = webhookAction.changeStatus();
        assertEquals("SUCCESS",result);
    }

    @Test
    public void testDeleteCustomWebhook(){
        CustomWebhooksDao.instance.getMCollection().drop();
        CustomWebhook customWebhook = new CustomWebhook(1,"webhook name","http://test.akto.io","","queryParam=test","body",Method.POST,10,null,0,0,0,ActiveStatus.ACTIVE);
        CustomWebhook customWebhook2 = new CustomWebhook(2,"webhook name","http://test.akto.io","","queryParam=test","body",Method.POST,10,null,0,0,0,ActiveStatus.ACTIVE);
        CustomWebhooksDao.instance.insertOne(customWebhook);
        CustomWebhooksDao.instance.insertOne(customWebhook2);

        WebhookAction webhookAction = new WebhookAction();
        webhookAction.setId(1);

        String result = webhookAction.deleteCustomWebhook();
        assertEquals("SUCCESS",result);
        List<CustomWebhook> customWebhooks = CustomWebhooksDao.instance.findAll(new BasicDBObject());
        assertEquals(1,customWebhooks.size());
    }

}
