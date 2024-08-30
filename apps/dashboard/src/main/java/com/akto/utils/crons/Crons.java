package com.akto.utils.crons;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.akto.dao.context.Context;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.dao.testing.DeleteTestRunsDao;
import com.akto.dao.traffic_metrics.RuntimeMetricsDao;
import com.akto.dao.traffic_metrics.TrafficAlertsDao;
import com.akto.dto.Account;
import com.akto.dto.testing.DeleteTestRuns;
import com.akto.dto.traffic_metrics.RuntimeMetrics;
import com.akto.dto.traffic_metrics.TrafficAlerts;
import com.akto.dto.traffic_metrics.TrafficAlerts.ALERT_TYPE;
import com.akto.util.AccountTask;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.utils.DeleteTestRunUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

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
}
