package com.akto.listener;

import com.akto.DaoInit;
import com.akto.action.observe.InventoryAction;
import com.akto.dao.MarkovDao;
import com.akto.dao.UsersDao;
import com.akto.dto.Markov;
import com.akto.dto.User;
import com.akto.dto.messaging.Message;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.notifications.email.WeeklyEmail;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.result.InsertOneResult;
import com.sendgrid.helpers.mail.Mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.context.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextListener;

public class InitializerListener implements ServletContextListener {
    private static final Logger logger = LoggerFactory.getLogger(InitializerListener.class);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static String domain = null;
    public static String getDomain() {
        if(domain == null) {
            if (true) {
                domain = "https://staging.akto.io:8443";
            } else {
                domain = "http://localhost:8080";
            }
        }

        return domain;
    }

    private void setUpWeeklyScheduler() {

        Map<Integer, Integer> dayToDelay = new HashMap<Integer, Integer>();
        dayToDelay.put(Calendar.FRIDAY, 5);
        dayToDelay.put(Calendar.SATURDAY, 4);
        dayToDelay.put(Calendar.SUNDAY, 3);
        dayToDelay.put(Calendar.MONDAY, 2);
        dayToDelay.put(Calendar.TUESDAY, 1);
        dayToDelay.put(Calendar.WEDNESDAY, 0);
        dayToDelay.put(Calendar.THURSDAY, 6);
        Calendar with = Calendar.getInstance();
        Date aDate = new Date();
        with.setTime(aDate);
        int dayOfWeek = with.get(Calendar.DAY_OF_WEEK);
        int delayInDays = dayToDelay.get(dayOfWeek);

        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    ChangesInfo changesInfo = getChangesInfo();
                    if (changesInfo == null || (changesInfo.newEndpointsLast7Days.size() + changesInfo.newSensitiveParams.size()) == 0) {
                        return;
                    }
                    String sendTo = UsersDao.instance.findOne(new BasicDBObject()).getLogin();
                    logger.info("Sending weekly email");
                    Mail mail = WeeklyEmail.buildWeeklyEmail(
                        changesInfo.newSensitiveParams.size(), 
                        changesInfo.newEndpointsLast7Days.size(), 
                        changesInfo.newEndpointsLast31Days.size(), 
                        sendTo, 
                        changesInfo.newEndpointsLast7Days, 
                        changesInfo.newSensitiveParams
                    );

                    WeeklyEmail.send(mail);

                } catch (Exception ex) {
                    ex.printStackTrace(); // or loggger would be better
                }
            }
        }, delayInDays, 7, TimeUnit.DAYS);

    }

    static class ChangesInfo {
        public List<String> newSensitiveParams = new ArrayList<>();
        public List<String> newEndpointsLast7Days = new ArrayList<>();
        public List<String> newEndpointsLast31Days = new ArrayList<>();
    }

    protected ChangesInfo getChangesInfo() {
        Context.accountId.set(1_000_000);
        InventoryAction inventoryAction = new InventoryAction();
        try {
            List<SingleTypeInfo> params = inventoryAction.fetchRecentParams(31 * 24 * 60 * 60);
            Map<String, Integer> newEndpointsToCountLast7Days = new HashMap<>();
            Map<String, Integer> newEndpointsToCountLast31Days = new HashMap<>();
            Set<String> newSensitiveParams = new HashSet<>();
            int now = Context.now();
            for(SingleTypeInfo param: params) {
                if ((now-param.getTimestamp()) < 7 * 24 * 60 * 60 ) {

                    newEndpointsToCountLast7Days.compute(param.getUrl(), (k, v) -> 1 + (v == null ? 0 : v));

                    SingleTypeInfo.Position position = param.findPosition();
                    if (param.getSubType().isSensitive(position)) {
                        newSensitiveParams.add(param.getParam() + " in " + param.getMethod() + ": " + param.getUrl());
                    }
                }

                newEndpointsToCountLast31Days.compute(param.getUrl(), (k, v) -> 1 + (v == null ? 0 : v));
            }

            ChangesInfo ret = new ChangesInfo();
            ret.newSensitiveParams.addAll(newSensitiveParams);
            for(String newURL: newEndpointsToCountLast7Days.keySet()) {
                if (newEndpointsToCountLast7Days.get(newURL) > 2) {
                    ret.newEndpointsLast7Days.add(newURL);
                }
            }
            for(String newURL: newEndpointsToCountLast31Days.keySet()) {
                if (newEndpointsToCountLast31Days.get(newURL) > 2) {
                    ret.newEndpointsLast31Days.add(newURL);
                }
            }
            return ret;
        } catch (Exception e) {
            logger.error("get new endpoints", e);
        }
        
        return null;
    }

    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {

        System.out.println("context initialized");

        // String mongoURI = "mongodb://write_ops:write_ops@cluster0-shard-00-00.yg43a.mongodb.net:27017,cluster0-shard-00-01.yg43a.mongodb.net:27017,cluster0-shard-00-02.yg43a.mongodb.net:27017/myFirstDatabase?ssl=true&replicaSet=atlas-qd3mle-shard-0&authSource=admin&retryWrites=true&w=majority";
        String mongoURI = System.getenv("AKTO_MONGO_CONN");
        System.out.println("MONGO URI " + mongoURI);


        DaoInit.init(new ConnectionString(mongoURI));

        SingleTypeInfo.init();

        setUpWeeklyScheduler();
    }
}
