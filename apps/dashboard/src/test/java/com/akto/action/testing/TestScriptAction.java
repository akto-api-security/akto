package com.akto.action.testing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.akto.dto.User;

public class TestScriptAction {

    @Test
    public void testAktoUser(){

        ScriptAction action = new ScriptAction();
        Map<String,Object> session = new HashMap<>();
        User user = new User();
        user.setLogin("test@akto.io");
        session.put("user",user);
        action.setSession(session);

        assertTrue(action.aktoUser());

        user.setLogin("test@notakto.com");
        session.put("user",user);
        action.setSession(session);
        assertFalse(action.aktoUser());

    }

}
