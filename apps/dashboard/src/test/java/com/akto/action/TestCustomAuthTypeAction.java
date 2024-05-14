package com.akto.action;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.CustomAuthTypeDao;
import com.akto.dto.CustomAuthType;
import com.akto.dto.User;

public class TestCustomAuthTypeAction extends MongoBasedTest {
    
    @Test
    public void testFetchCustomAuthTypes(){
        CustomAuthTypeDao.instance.getMCollection().drop();
        List<CustomAuthType> customAuthTypes = new ArrayList<>();
        customAuthTypes.add(new CustomAuthType("auth1", new ArrayList<>(Collections.singletonList("authtoken")),new ArrayList<>(Collections.singletonList("authtoken")), true, ACCOUNT_ID, null, null));
        customAuthTypes.add(new CustomAuthType("auth2", new ArrayList<>(Collections.singletonList("newauthtoken")),new ArrayList<>(Collections.singletonList("authtoken")), true, ACCOUNT_ID, null, null));
        CustomAuthTypeDao.instance.insertMany(customAuthTypes);

        CustomAuthTypeAction customAuthTypeAction = new CustomAuthTypeAction();

        String result = customAuthTypeAction.fetchCustomAuthTypes();
        assertEquals("SUCCESS", result);
        assertEquals(2, customAuthTypeAction.getCustomAuthTypes().size());
    }

    @Test
    public void testAddCustomAuthType(){
        CustomAuthTypeDao.instance.getMCollection().drop();
        CustomAuthTypeAction customAuthTypeAction = new CustomAuthTypeAction();

        Map<String,Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("test@akto.io");
        session.put("user",user);
        customAuthTypeAction.setSession(session);

        customAuthTypeAction.setName("auth1");
        customAuthTypeAction.setHeaderKeys(new ArrayList<>(Collections.singletonList("somekey")));
        customAuthTypeAction.setPayloadKeys(new ArrayList<>(Collections.singletonList("somekey")));
        customAuthTypeAction.setActive(true);

        String result = customAuthTypeAction.addCustomAuthType();
        assertEquals("SUCCESS", result);
        assertEquals(1,customAuthTypeAction.getCustomAuthTypes().size());
    }

    @Test
    public void testUpdateCustomAuthType(){
        CustomAuthTypeDao.instance.getMCollection().drop();
        CustomAuthType customAuthType = new CustomAuthType("auth1", new ArrayList<>(Collections.singletonList("authtoken")),new ArrayList<>(Collections.singletonList("authtoken")), true, ACCOUNT_ID, null, null);
        CustomAuthTypeDao.instance.insertOne(customAuthType);
        CustomAuthTypeAction customAuthTypeAction = new CustomAuthTypeAction();

        Map<String,Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("test@akto.io");
        user.setId(ACCOUNT_ID);
        session.put("user",user);
        customAuthTypeAction.setSession(session);

        customAuthTypeAction.setName("auth1");
        customAuthTypeAction.setHeaderKeys(new ArrayList<>(Collections.singletonList("somekey")));
        customAuthTypeAction.setPayloadKeys(new ArrayList<>(Collections.singletonList("somekey")));
        customAuthTypeAction.setActive(false);

        String result = customAuthTypeAction.updateCustomAuthType();
        assertEquals("SUCCESS", result);
        assertEquals(false,customAuthTypeAction.getCustomAuthTypes().get(0).getActive());

    }

    @Test
    public void testUpdateCustomAuthTypeStatus(){
        CustomAuthTypeDao.instance.getMCollection().drop();
        CustomAuthType customAuthType = new CustomAuthType("auth1", new ArrayList<>(Collections.singletonList("authtoken")),new ArrayList<>(Collections.singletonList("authtoken")), true, ACCOUNT_ID, null, null);
        CustomAuthTypeDao.instance.insertOne(customAuthType);
        CustomAuthTypeAction customAuthTypeAction = new CustomAuthTypeAction();

        Map<String,Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("test@akto.io");
        user.setId(ACCOUNT_ID);
        session.put("user",user);
        customAuthTypeAction.setSession(session);

        customAuthTypeAction.setName("auth1");
        customAuthTypeAction.setActive(false);

        String result = customAuthTypeAction.updateCustomAuthTypeStatus();
        assertEquals("SUCCESS", result);
        assertEquals(false,customAuthTypeAction.getCustomAuthTypes().get(0).getActive());

    }
}
