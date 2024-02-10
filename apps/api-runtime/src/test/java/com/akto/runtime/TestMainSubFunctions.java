package com.akto.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.*;

import com.akto.MongoBasedTest;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import org.junit.Before;
import org.junit.Test;

public class TestMainSubFunctions extends MongoBasedTest{
    private static int currAccountId = 0;
    ObjectMapper mapper = new ObjectMapper();

    @Before
    public void changeAccountId() {
        Context.accountId.set(currAccountId);
        currAccountId += 1;
        Context.accountId.set(currAccountId);
    }

    @Test
    public void testTryForCollectionNameWithoutCidr() throws JsonProcessingException {
        int vxlan_id = 10000;
        String group_name = "bb";

        ApiCollectionsDao.instance.insertOne(new ApiCollection(
                vxlan_id, null, 0, new HashSet<>(), null, vxlan_id, false, true
        ));

        Map<String, Object> m = new HashMap<>();
        m.put(Main.GROUP_NAME, group_name);
        m.put(Main.VXLAN_ID, vxlan_id);
        
        String message = mapper.writeValueAsString(m);
        Main.tryForCollectionName(message);

        ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq(ApiCollection.VXLAN_ID,vxlan_id ));
        assertEquals(apiCollection.getVxlanId(), vxlan_id);
        assertEquals(apiCollection.getName(), group_name);
        assertNull(apiCollection.getHostName());

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(Filters.eq("_id", Context.accountId.get()));
        assertNull(accountSettings);
    }


    @Test
    public void testTryForCollectionNameWithCidr() throws JsonProcessingException {
        int vxlan_id = 10000;
        String group_name = "bb";
        List<String> vpc_cidr = Arrays.asList("192.1.1.1/16", "193.1.1.1/16");

        ApiCollectionsDao.instance.insertOne(new ApiCollection(
                vxlan_id, null, 0, new HashSet<>(), null, vxlan_id, false, true
        ));

        Map<String, Object> m = new HashMap<>();
        m.put(Main.GROUP_NAME, group_name);
        m.put(Main.VXLAN_ID, vxlan_id);
        m.put(Main.VPC_CIDR, vpc_cidr);
        m.put(Main.ACCOUNT_ID, ""+Context.accountId.get());
        
        String message = mapper.writeValueAsString(m);
        Main.tryForCollectionName(message);

        ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq(ApiCollection.VXLAN_ID,vxlan_id ));
        assertEquals(apiCollection.getVxlanId(), vxlan_id);
        assertEquals(apiCollection.getName(), group_name);
        assertNull(apiCollection.getHostName());

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(Filters.eq("_id", Context.accountId.get()));
        assertNotNull(accountSettings);
        assertEquals(accountSettings.getId(), Context.accountId.get());
        assertEquals(accountSettings.getPrivateCidrList().size(), 2);
        assertTrue(accountSettings.getPrivateCidrList().containsAll(vpc_cidr));

        Main.tryForCollectionName(message);
        List<AccountSettings> accountSettingsList = AccountSettingsDao.instance.findAll(new BasicDBObject());
        assertEquals(accountSettingsList.size(), 1);
        assertEquals(accountSettingsList.get(0).getPrivateCidrList().size(), 2);

        List<ApiCollection > apiCollectionList = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        assertEquals(apiCollectionList.size(), 1);
    }
}
