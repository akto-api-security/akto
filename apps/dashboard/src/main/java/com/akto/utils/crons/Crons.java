package com.akto.utils.crons;

import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.HistoricalDataDao;
import com.akto.dao.TestingAlertsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.HistoricalData;
import com.akto.dto.TestingAlerts;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.dao.testing.DeleteTestRunsDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.traffic_metrics.RuntimeMetricsDao;
import com.akto.dao.traffic_metrics.TrafficAlertsDao;
import com.akto.dto.Account;
import com.akto.dto.testing.DeleteTestRuns;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.traffic_metrics.RuntimeMetrics;
import com.akto.dto.traffic_metrics.TrafficAlerts;
import com.akto.dto.traffic_metrics.TrafficAlerts.ALERT_TYPE;
import com.akto.util.AccountTask;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.http_util.CoreHTTPClient;
import com.akto.utils.DeleteTestRunUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Updates;
import com.slack.api.Slack;
import com.slack.api.util.http.SlackHttpClient;
import com.slack.api.webhook.WebhookResponse;

import okhttp3.OkHttpClient;


public class Crons {

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static LoggerMaker logger = new LoggerMaker(Crons.class, LoggerMaker.LogDb.DASHBOARD);
    private static Integer oldMetricThreshold = 30 * 60;

    public void deleteTestRunsScheduler(){
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run(){
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            List<DeleteTestRuns> deleteTestRunsList = DeleteTestRunsDao.instance.findAll(Filters.empty());
                            if(deleteTestRunsList != null){
                                for(DeleteTestRuns deleteTestRun : deleteTestRunsList){
                                    List<ObjectId> latestSummaryIds = deleteTestRun.getLatestTestingSummaryIds();
                                    if(DeleteTestRunUtils.isTestRunDeleted(deleteTestRun)){
                                        DeleteTestRunsDao.instance.getMCollection().deleteOne(Filters.in(DeleteTestRuns.LATEST_TESTING_SUMMARY_IDS, latestSummaryIds));
                                    }else{
                                        DeleteTestRunUtils.deleteTestRunsFromDb(deleteTestRun);
                                    }
                                }
                            }
                            logger.infoAndAddToDb("Starting to delete pending test runs");
                            InitializerListener.deleteFileUploads(Context.accountId.get());
                            logger.infoAndAddToDb("Finished deleting pending test runs");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                },"delete-test-runs");
            }
        }, 0 , 1, TimeUnit.DAYS);
    }

    public void trafficAlertsScheduler(){
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run(){
                AccountTask.instance.executeTaskHybridAccounts(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            int accId = Context.accountId.get();
                            RuntimeMetrics runtimeMetrics = RuntimeMetricsDao.instance.findOne(Filters.empty());
                            if (runtimeMetrics == null) {
                                logger.infoAndAddToDb("Skipping traffic alert cron " + accId);
                                return;
                            }
                            List<Bson> pipeline = new ArrayList<>();
                            int startTs = Context.now() - oldMetricThreshold;
                            int endTs = Context.now();

                            pipeline.add(Aggregates.match(RuntimeMetricsDao.buildFilters(startTs, endTs)));
                            BasicDBObject groupedId = 
                                new BasicDBObject("name", "$name");
                            pipeline.add(Aggregates.group(groupedId, Accumulators.sum("totalVal", "$val")));
                            MongoCursor<BasicDBObject> cursor = RuntimeMetricsDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
                            int cnt = 0;
                            while(cursor.hasNext()) {
                                cnt++;
                                BasicDBObject obj = cursor.next();
                                BasicDBObject metric = (BasicDBObject) obj.get("_id");
                                if (metric.getString("name").equalsIgnoreCase("RT_KAFKA_RECORD_COUNT")) {
                                    Double val = obj.getDouble("totalVal");
                                    if (val == 0) {
                                        Bson filters = Filters.and(
                                            Filters.eq("alertType", ALERT_TYPE.TRAFFIC_STOPPED),
                                            Filters.eq("lastDismissed", 0)
                                        );
                                        TrafficAlerts trafficAlerts = TrafficAlertsDao.instance.findOne(filters);
                                        if (trafficAlerts == null) {
                                            TrafficAlertsDao.instance.insertOne(new TrafficAlerts("Runtime Stopped Receiving Traffic " + "${" + startTs + "}", Context.now(), ALERT_TYPE.TRAFFIC_STOPPED, Severity.HIGH, 0));
                                        }
                                    }
                                }
                            }
                            if (cnt == 0) {
                                Bson filters = Filters.and(
                                    Filters.eq("alertType", ALERT_TYPE.CYBORG_STOPPED_RECEIVING_TRAFFIC),
                                    Filters.eq("lastDismissed", 0)
                                );
                                TrafficAlerts trafficAlerts = TrafficAlertsDao.instance.findOne(filters);
                                if (trafficAlerts == null) {
                                    TrafficAlertsDao.instance.insertOne(new TrafficAlerts("Akto Stopped Receiving Traffic " + "${" + startTs + "}", Context.now(), ALERT_TYPE.CYBORG_STOPPED_RECEIVING_TRAFFIC, Severity.HIGH, 0));
                                }
                            }
                        } catch (Exception e) {
                            logger.errorAndAddToDb(e, "Error in trafficAlertsScheduler " + e.toString());
                        }
                    }
                },"traffic-alerts-scheduler");
            }
        }, 0 , 5, TimeUnit.MINUTES);
    }

    public void testingAlertsScheduler(){
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run(){
                logger.infoAndAddToDb("testing alerts scheduler triggered", LogDb.DASHBOARD);
                Bson filters = Filters.and(
                    Filters.lte("updatedTs", Context.now() - 15 * 60),
                    Filters.eq("status", "SCHEDULED"),
                    Filters.eq("alertSent", false)
                );
                List<TestingAlerts> testingAlerts = TestingAlertsDao.instance.findAll(filters);

                for (TestingAlerts alert: testingAlerts) {
                    TestingRun tr = TestingRunDao.instance.findOne(Filters.eq("_id", alert.getTestRunId()));
                    if (tr != null && tr.getScheduleTimestamp() < Context.now()) {
                        OkHttpClient httpClient = CoreHTTPClient.client.newBuilder().build();
                        SlackHttpClient slackHttpClient = new SlackHttpClient(httpClient);
                        Slack slack = Slack.getInstance(slackHttpClient);
                        String webhookUrl = "https://hooks.slack.com/triggers/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";
                        try {
                            BasicDBObject payload = new BasicDBObject();
                            payload.put("accountId", alert.getAccountId());
                            payload.put("testRunId", alert.getTestRunId().toHexString());
                            logger.infoAndAddToDb("Test alert payload:" + payload, LogDb.DASHBOARD);
                            WebhookResponse response = slack.send(webhookUrl, payload.toJson());
                            logger.infoAndAddToDb("Test alert Response: " + response.getBody(), LogDb.DASHBOARD);
                            Bson updates = Updates.combine(
                                Updates.set("alertSent", true)
                            );
                            TestingAlertsDao.instance.getMCollection().findOneAndUpdate(Filters.eq("testRunId", tr.getId()), updates, new FindOneAndUpdateOptions());
                        } catch (Exception e) {
                            e.printStackTrace();
                            logger.errorAndAddToDb(e, "Error while sending testing alert: " + e.getMessage(), LogDb.DASHBOARD);
                        }
                    }
                }
            }
        }, 0 , 5, TimeUnit.MINUTES);
    }

    public static void insertHistoricalData(){
        int currentTime = Context.now();
        Map<Integer, HistoricalData> historicalDataMap = new HashMap<>();
        MongoCursor<ApiInfo> cursor = ApiInfoDao.instance.getMCollection().find().cursor();

        while (cursor.hasNext()) {
            ApiInfo apiInfo = cursor.next();
            List<Integer> collectionIds = apiInfo.getCollectionIds();
            float riskScore = apiInfo.getRiskScore();

            for (Integer collectionId : collectionIds) {
                HistoricalData historicalData = historicalDataMap.getOrDefault(collectionId, new HistoricalData(collectionId, 0, 0, 0, currentTime));
                historicalData.setTotalApis(historicalData.getTotalApis() + 1);
                historicalData.setRiskScore(historicalData.getRiskScore() + riskScore);

                if (apiInfo.getLastTested() > (Context.now() - 30 * 24 * 60 * 60)) {
                    historicalData.setApisTested(historicalData.getApisTested() + 1);
                }
                historicalDataMap.put(collectionId, historicalData);
            }
        }

        List<HistoricalData> values = new ArrayList<>(historicalDataMap.values());
        HistoricalDataDao.instance.insertMany(values);

        cursor.close();
    }

    public void insertHistoricalDataJob() {
        Runnable task = new Runnable() {
            public void run() {
                AccountTask.instance.executeTaskHybridAccounts(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        insertHistoricalData();
                    }
                }, "historical-data-scheduler");
            }
        };

        long initialDelay = calculateInitialDelay();
        long period = TimeUnit.DAYS.toMillis(1); // 24 hours period

        scheduler.scheduleAtFixedRate(task, initialDelay, period, TimeUnit.MILLISECONDS);
    }

    public void insertHistoricalDataJobForOnPrem(){
        Runnable task = new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        insertHistoricalData();
                    }
                }, "historical-data-scheduler");
            }
        };

        long initialDelay = calculateInitialDelay();
        long period = TimeUnit.DAYS.toMillis(1); // 24 hours period

        scheduler.scheduleAtFixedRate(task, initialDelay, period, TimeUnit.MILLISECONDS);
    }

    private static long calculateInitialDelay() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime nextRun = now.withHour(23).withMinute(59).withSecond(0).withNano(0);

        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1); // Schedule for the next day
        }

        return Duration.between(now, nextRun).toMillis();
    }


}
