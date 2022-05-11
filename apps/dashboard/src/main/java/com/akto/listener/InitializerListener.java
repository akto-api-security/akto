package com.akto.listener;

import com.akto.DaoInit;
import com.akto.action.observe.InventoryAction;
import com.akto.dao.*;
import com.akto.dto.BackwardCompatibility;
import com.akto.dto.FilterSampleData;
import com.akto.dto.Markov;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.User;
import com.akto.dto.messaging.Message;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.notifications.email.WeeklyEmail;
import com.akto.notifications.slack.DailyUpdate;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.InsertOneResult;
import com.sendgrid.helpers.mail.Mail;
import com.slack.api.Slack;
import com.slack.api.webhook.WebhookResponse;

import org.apache.commons.lang3.StringUtils;
import org.bson.BsonDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.context.Context;
import com.akto.dao.notifications.SlackWebhooksDao;

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
                    ChangesInfo changesInfo = getChangesInfo(31, 7);
                    if (changesInfo == null || (changesInfo.newEndpointsLast7Days.size() + changesInfo.newSensitiveParams.size()) == 0) {
                        return;
                    }
                    String sendTo = UsersDao.instance.findOne(new BasicDBObject()).getLogin();
                    logger.info("Sending weekly email");
                    Mail mail = WeeklyEmail.buildWeeklyEmail(
                        changesInfo.recentSentiiveParams, 
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

    private void setUpDailyScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    Context.accountId.set(1_000_000);
                    List<SlackWebhook> listWebhooks = SlackWebhooksDao.instance.findAll(new BasicDBObject());
                    if (listWebhooks == null || listWebhooks.isEmpty()) {
                        return;
                    }

                    Slack slack = Slack.getInstance();

                    for(SlackWebhook slackWebhook: listWebhooks) {
                        System.out.println(slackWebhook); 

                        ChangesInfo ci = getChangesInfo(slackWebhook.getLargerDuration(), slackWebhook.getSmallerDuration());
                        if (ci == null || (ci.newEndpointsLast7Days.size() + ci.newSensitiveParams.size() + ci.recentSentiiveParams) == 0) {
                            return;
                        }
    
                        DailyUpdate dailyUpdate = new DailyUpdate(0, 0, ci.newSensitiveParams.size(), ci.newEndpointsLast7Days.size(), ci.recentSentiiveParams, ci.newSensitiveParams, "");
    
                        String webhookUrl = slackWebhook.getWebhook();
                        String payload = dailyUpdate.toJSON();
                        System.out.println(payload);
                        WebhookResponse response = slack.send(webhookUrl, payload);
                        System.out.println(response); 
                    }

                } catch (Exception ex) {
                    ex.printStackTrace(); // or loggger would be better
                }
            }
        }, 0, 24, TimeUnit.HOURS);

    }

    static class ChangesInfo {
        public List<String> newSensitiveParams = new ArrayList<>();
        public List<String> newEndpointsLast7Days = new ArrayList<>();
        public List<String> newEndpointsLast31Days = new ArrayList<>();
        public int totalSensitiveParams = 0;
        public int recentSentiiveParams = 0;
    }

    protected ChangesInfo getChangesInfo(int newEndpointsDays, int newSensitiveParamsDays) {
        try {
            
            ChangesInfo ret = new ChangesInfo();
            List<BasicDBObject> newEndpointsSmallerDuration = new InventoryAction().fetchRecentEndpoints(newSensitiveParamsDays * 24 * 60 * 60);
            List<BasicDBObject> newEndpointsBiggerDuration = new InventoryAction().fetchRecentEndpoints(newEndpointsDays * 24 * 60 * 60);

            for (BasicDBObject singleTypeInfo: newEndpointsSmallerDuration) {
                singleTypeInfo = (BasicDBObject) (singleTypeInfo.getOrDefault("_id", new BasicDBObject()));
                ret.newEndpointsLast7Days.add(singleTypeInfo.getString("method") + singleTypeInfo.getString("url"));
            }
    
            for (BasicDBObject singleTypeInfo: newEndpointsBiggerDuration) {
                singleTypeInfo = (BasicDBObject) (singleTypeInfo.getOrDefault("_id", new BasicDBObject()));
                ret.newEndpointsLast31Days.add(singleTypeInfo.getString("method") + singleTypeInfo.getString("url"));
            }
    
            List<SingleTypeInfo> sensitiveParamsList = new InventoryAction().fetchSensitiveParams();
            ret.totalSensitiveParams = sensitiveParamsList.size();
            ret.recentSentiiveParams = 0;
            int now = Context.now();
            int delta = newSensitiveParamsDays * 24 * 60 * 60;
            Map<String, Set<String>> endpointToSubTypes = new HashMap<>();
            for(SingleTypeInfo sti: sensitiveParamsList) {
                String key = sti.getMethod() + " " + sti.getUrl();
                String value = sti.getSubType().getName();
                if (sti.getTimestamp() >= now - delta) {
                    ret.recentSentiiveParams ++;
                    Set<String> subTypes = endpointToSubTypes.get(key);
                    if (subTypes == null) {
                        subTypes = new HashSet<>();
                        endpointToSubTypes.put(key, subTypes);
                    }
                    subTypes.add(value);
                }
            }

            for(String key: endpointToSubTypes.keySet()) {
                ret.newSensitiveParams.add(key + ": " + StringUtils.join(endpointToSubTypes.get(key), ","));
            }

            return ret;
        } catch (Exception e) {
            logger.error("get new endpoints", e);
        }
        
        return null;
    }

    public void dropFilterSampleDataCollection(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getDropFilterSampleData() == 0) {
            FilterSampleDataDao.instance.getMCollection().drop();
        }
        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.DROP_FILTER_SAMPLE_DATA, Context.now())
        );
    }

    public void resetSingleTypeInfoCount(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getResetSingleTypeInfoCount() == 0) {
            SingleTypeInfoDao.instance.resetCount();
        }

        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.RESET_SINGLE_TYPE_INFO_COUNT, Context.now())
        );

    }

    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {

        System.out.println("context initialized");

        // String mongoURI = "mongodb://write_ops:write_ops@cluster0-shard-00-00.yg43a.mongodb.net:27017,cluster0-shard-00-01.yg43a.mongodb.net:27017,cluster0-shard-00-02.yg43a.mongodb.net:27017/myFirstDatabase?ssl=true&replicaSet=atlas-qd3mle-shard-0&authSource=admin&retryWrites=true&w=majority";
        String mongoURI = System.getenv("AKTO_MONGO_CONN");
        System.out.println("MONGO URI " + mongoURI);


        DaoInit.init(new ConnectionString(mongoURI));

        Context.accountId.set(1_000_000);
        BackwardCompatibility backwardCompatibility = BackwardCompatibilityDao.instance.findOne(new BasicDBObject());
        if (backwardCompatibility == null) {
            backwardCompatibility = new BackwardCompatibility();
            BackwardCompatibilityDao.instance.insertOne(backwardCompatibility);
        }

        // backward compatibility
        dropFilterSampleDataCollection(backwardCompatibility);
        resetSingleTypeInfoCount(backwardCompatibility);

        SingleTypeInfo.init();

        setUpWeeklyScheduler();
        setUpDailyScheduler();
    }
}
