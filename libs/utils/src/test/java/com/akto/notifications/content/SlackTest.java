package com.akto.notifications.content;

import java.util.ArrayList;
import java.util.HashMap;

import com.akto.notifications.slack.DailyUpdate;

import org.junit.Test;

public class SlackTest {

    @Test
    public void testDailyUpdate() {

        DailyUpdate dailyUpdate = new DailyUpdate(10, 100, 5, 20, 7, 8, 10, 100, new HashMap<>(), "http://localhost:8080");
        System.out.println(dailyUpdate.toJSON());

    }
    
}
