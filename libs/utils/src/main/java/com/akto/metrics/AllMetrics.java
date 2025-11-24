package com.akto.metrics;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.billing.Organization;
import com.akto.dto.metrics.MetricData;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AllMetrics {

    private final static int METRIC_SEND_LIMIT = 5;

    public void init(LogDb module){
        loggerMaker.setDb(module);

        String prefix = "RT_";
        if(LogDb.THREAT_DETECTION.equals(module)){
            prefix = "TD_";
        } else if(LogDb.DATA_INGESTION.equals(module)){
            prefix = "DI_";
        }
        int accountId = Context.accountId.get();

        Organization organization = DataActorFactory.fetchInstance().fetchOrganization(accountId);
        String orgId = organization.getId();

        /*
         * Any metric added here must be added to logs-collector as well.
         * Repo: https://github.com/akto-api-security/telemetry/blob/master/collector/main.py
         */
        runtimeKafkaRecordCount = new SumMetric(prefix+"KAFKA_RECORD_COUNT", 60, accountId, orgId);
        runtimeKafkaRecordSize = new SumMetric(prefix+"KAFKA_RECORD_SIZE", 60, accountId, orgId);
        runtimeProcessLatency = new LatencyMetric(prefix+"KAFKA_LATENCY", 60, accountId, orgId);
        kafkaRecordsLagMax = new SumMetric(prefix+"KAFKA_RECORDS_LAG_MAX", 60, accountId, orgId);
        kafkaRecordsConsumedRate = new SumMetric(prefix+"KAFKA_RECORDS_CONSUMED_RATE", 60, accountId, orgId);
        kafkaFetchAvgLatency = new LatencyMetric(prefix+"KAFKA_FETCH_AVG_LATENCY", 60, accountId, orgId);
        kafkaBytesConsumedRate = new SumMetric(prefix+"KAFKA_BYTES_CONSUMED_RATE", 60, accountId, orgId);
        /*
         * TODO: initialize metrics based on the module, to limit avoidable calls.
         * Note: dataIngestionApiCount is NOT initialized here - use initDataIngestion() instead
         */

        metrics = Arrays.asList(runtimeKafkaRecordCount, runtimeKafkaRecordSize, runtimeProcessLatency,
                postgreSampleDataInsertedCount, postgreSampleDataInsertLatency, mergingJobLatency, mergingJobUrlsUpdatedCount,
                staleSampleDataCleanupJobLatency, staleSampleDataDeletedCount, mergingJobUrlUpdateLatency, cyborgCallLatency,
                cyborgCallCount, cyborgDataSize, testingRunCount, testingRunLatency, totalSampleDataCount, sampleDataFetchLatency,
                sampleDataFetchCount, pgDataSizeInMb, kafkaOffset, kafkaRecordsLagMax, kafkaRecordsConsumedRate, kafkaFetchAvgLatency,
                kafkaBytesConsumedRate, cyborgNewApiCount, cyborgTotalApiCount, deltaCatalogNewCount, deltaCatalogTotalCount,
                cyborgApiPayloadSize, multipleSampleDataFetchLatency);
                // Note: dataIngestionApiCount is NOT in this list - added separately by initDataIngestion()

        if(executorService == null){
            executorService  = Executors.newScheduledThreadPool(1);
        }

        executorService.scheduleAtFixedRate(() -> {
            try {
                Context.accountId.set(accountId);
                BasicDBList list = new BasicDBList();
                for (Metric m : metrics) {
                    if (m == null) {
                        continue;
                    }
                    float metric = m.getMetricAndReset();

                    BasicDBObject metricsData = new BasicDBObject();
                    metricsData.put("metric_id", m.metricId);
                    metricsData.put("val", metric);
                    metricsData.put("org_id", m.orgId);
                    metricsData.put("instance_id", instance_id);
                    metricsData.put("account_id", m.accountId);
                    list.add(metricsData);
                    if (list.size() >= METRIC_SEND_LIMIT) {
                        sendDataToAkto(list);
                        list.clear();
                    }
                }
                if(!list.isEmpty()) {
                    sendDataToAkto(list);
                }
            } catch (Exception e){
                loggerMaker.errorAndAddToDb(e, "Error while sending metrics to akto: " + e.getMessage());
            }
        }, 0, 60, TimeUnit.SECONDS);
    }

    /**
     * Simplified init for data-ingestion-service - only initializes data ingestion metrics
     * Call this instead of init() to avoid sending zeros for runtime metrics
     */
    public void initDataIngestion(int accountId) {
        String orgId = null;
        try {
            Organization organization = DataActorFactory.fetchInstance().fetchOrganization(accountId);
            orgId = organization.getId();
        } catch (Exception e) {
        }

        // Only initialize data ingestion metric
        dataIngestionApiCount = new SumMetric("DATA_INGESTION_API_COUNT", 60, accountId, orgId);

        // Only include the metric we actually track
        metrics = Arrays.asList(dataIngestionApiCount);

        if(executorService == null){
            executorService  = Executors.newScheduledThreadPool(1);
        }

        executorService.scheduleWithFixedDelay(() -> {
            try {
                Context.accountId.set(accountId);
                BasicDBList list = new BasicDBList();
                for (Metric m : metrics) {
                    if (m == null) {
                        continue;
                    }
                    float metric = m.getMetricAndReset();

                    BasicDBObject metricsData = new BasicDBObject();
                    metricsData.put("metric_id", m.metricId);
                    metricsData.put("val", metric);
                    metricsData.put("org_id", m.orgId);
                    metricsData.put("instance_id", instance_id);
                    metricsData.put("account_id", m.accountId);
                    list.add(metricsData);
                }
                if(!list.isEmpty()) {
                    sendDataToAkto(list);
                }
            } catch (Exception e){
                loggerMaker.errorAndAddToDb("Error while sending data ingestion metrics: " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    private AllMetrics(){}

    public static AllMetrics instance = new AllMetrics();

    private final static LoggerMaker loggerMaker = new LoggerMaker(AllMetrics.class, LogDb.RUNTIME);
    private static final String instance_id = UUID.randomUUID().toString();
    private Metric runtimeKafkaRecordCount = null;
    private Metric runtimeKafkaRecordSize = null;
    private Metric runtimeProcessLatency = null;
    private Metric postgreSampleDataInsertedCount = null;
    private Metric postgreSampleDataInsertLatency = null;
    private Metric mergingJobLatency = null;
    private Metric mergingJobUrlsUpdatedCount = null;
    private Metric staleSampleDataCleanupJobLatency = null;
    private Metric staleSampleDataDeletedCount = null;
    private Metric mergingJobUrlUpdateLatency = null;
    private Metric cyborgCallLatency = null;
    private Metric cyborgCallCount = null;
    private Metric cyborgDataSize = null;
    private Metric testingRunCount = null;
    private Metric testingRunLatency = null;
    private Metric totalSampleDataCount = null;
    private Metric sampleDataFetchLatency = null;
    private Metric sampleDataFetchCount = null;
    private Metric pgDataSizeInMb = null;
    private Metric kafkaOffset = null;
    private Metric kafkaRecordsLagMax = null;
    private Metric kafkaRecordsConsumedRate = null;
    private Metric kafkaFetchAvgLatency = null;
    private Metric kafkaBytesConsumedRate = null;
    private Metric cyborgNewApiCount = null;
    private Metric cyborgTotalApiCount = null;
    private Metric deltaCatalogNewCount = null;
    private Metric deltaCatalogTotalCount = null;
    private Metric cyborgApiPayloadSize = null;
    private Metric multipleSampleDataFetchLatency = null;
    private Metric dataIngestionApiCount = null;

    private List<Metric> metrics = null;

    public void setRuntimeKafkaRecordCount(float val){
        if(runtimeKafkaRecordCount != null)
            runtimeKafkaRecordCount.record(val);
    }

    public void setRuntimeKafkaRecordSize(float val){
        if(runtimeKafkaRecordSize != null)
            runtimeKafkaRecordSize.record(val);
    }

    public void setRuntimeProcessLatency(float val){
        if(runtimeProcessLatency != null)
            runtimeProcessLatency.record(val);
    }

    public void setPostgreSampleDataInsertedCount(float val){
        if(postgreSampleDataInsertedCount != null)
            postgreSampleDataInsertedCount.record(val);
    }

    public void setPostgreSampleDataInsertLatency(float val){
        if(postgreSampleDataInsertLatency != null)
            postgreSampleDataInsertLatency.record(val);
    }

    public void setMergingJobLatency(float val){
        if(mergingJobLatency != null)
            mergingJobLatency.record(val);
    }

    public void setMergingJobUrlsUpdatedCount(float val){
        if(mergingJobUrlsUpdatedCount != null)
            mergingJobUrlsUpdatedCount.record(val);
    }

    public void setStaleSampleDataCleanupJobLatency(float val){
        if(staleSampleDataCleanupJobLatency != null)
            staleSampleDataCleanupJobLatency.record(val);
    }

    public void setStaleSampleDataDeletedCount(float val){
        if(staleSampleDataDeletedCount != null)
            staleSampleDataDeletedCount.record(val);
    }

    public void setMergingJobUrlUpdateLatency(float val){
        if(mergingJobUrlUpdateLatency != null)
            mergingJobUrlUpdateLatency.record(val);
    }

    public void setCyborgCallLatency(float val){
        if(cyborgCallLatency != null)
            cyborgCallLatency.record(val);
    }

    public void setCyborgCallCount(float val){
        if(cyborgCallCount != null)
            cyborgCallCount.record(val);
    }

    public void setCyborgDataSize(float val){
        if(cyborgDataSize != null)
            cyborgDataSize.record(val);
    }

    public void setTestingRunCount(float val){
        if(testingRunCount != null)
            testingRunCount.record(val);
    }

    public void setTestingRunLatency(float val){
        if(testingRunLatency != null)
            testingRunLatency.record(val);
    }

    public void setTotalSampleDataCount(float val){
        if(totalSampleDataCount != null)
            totalSampleDataCount.record(val);
    }

    public void setSampleDataFetchLatency(float val){
        if(sampleDataFetchLatency != null)
            sampleDataFetchLatency.record(val);
    }

    public void setSampleDataFetchCount(float val){
        if(sampleDataFetchCount != null)
            sampleDataFetchCount.record(val);
    }

    public void setPgDataSizeInMb(float val){
        if(pgDataSizeInMb != null)
            pgDataSizeInMb.record(val);
    }

    public void setKafkaOffset(float val){
        if(kafkaOffset != null)
            kafkaOffset.record(val);
    }

    public void setKafkaRecordsLagMax(float val){
        if(kafkaRecordsLagMax != null)
            kafkaRecordsLagMax.record(val);
    }

    public void setKafkaRecordsConsumedRate(float val){
        if(kafkaRecordsConsumedRate != null)
            kafkaRecordsConsumedRate.record(val);
    }

    public void setKafkaFetchAvgLatency(float val){
        if(kafkaFetchAvgLatency != null)
            kafkaFetchAvgLatency.record(val);
    }

    public void setKafkaBytesConsumedRate(float val){
        if(kafkaBytesConsumedRate != null)
            kafkaBytesConsumedRate.record(val);
    }

    public void setCyborgNewApiCount(float val){
        if(cyborgNewApiCount != null)
            cyborgNewApiCount.record(val);
    }

    public void setCyborgTotalApiCount(float val){
        if(cyborgTotalApiCount != null)
            cyborgTotalApiCount.record(val);
    }

    public void setDeltaCatalogNewCount(float val){
        if(deltaCatalogNewCount != null)
            deltaCatalogNewCount.record(val);
    }

    public void setDeltaCatalogTotalCount(float val){
        if(deltaCatalogTotalCount != null)
            deltaCatalogTotalCount.record(val);
    }

    public void setCyborgApiPayloadSize(float val){
        if(cyborgApiPayloadSize != null)
            cyborgApiPayloadSize.record(val);
    }

    public void setMultipleSampleDataFetchLatency(float val){
        if(multipleSampleDataFetchLatency != null)
            multipleSampleDataFetchLatency.record(val);
    }

    public void setDataIngestionApiCount(float val){
        if(dataIngestionApiCount != null)
            dataIngestionApiCount.record(val);
    }


    private static ScheduledExecutorService executorService;

    enum MetricType{
        LATENCY, SUM
    }

    public abstract class Metric{
        String metricId;
        int timestamp;
        int periodInSecs;
        String orgId;
        int accountId;

        public Metric(String metricId, int periodInSecs, int accountId, String orgId){
            this.metricId = metricId;
            this.periodInSecs = periodInSecs;
            this.timestamp = Context.now();
            this.accountId = accountId;
            this.orgId = orgId;
        }


        public Metric(String metricId,  int periodInSecs) {
            this.metricId = metricId;
            this.periodInSecs = periodInSecs;
            this.timestamp = Context.now();
        }

        public abstract void record(float val);

        abstract float getMetric();

        abstract float getMetricAndReset();

        abstract MetricType getMetricType();

    }

    class LatencyMetric extends Metric{
        float total;
        int count;

        public LatencyMetric(String metricId, int periodInSecs) {
            super(metricId, periodInSecs);
        }

        public LatencyMetric(String metricId, int periodInSecs, int accountId, String orgId) {
            super(metricId, periodInSecs, accountId, orgId);
        }

        @Override
        public void record(float val) {
            count++;
            total += val;
        }

        @Override
        float getMetric() {
            if(count == 0){
                return 0;
            }
            return total/count;
        }

        @Override
        MetricType getMetricType() {
            return MetricType.LATENCY;
        }

        @Override
        float getMetricAndReset() {
            float val = getMetric();
            this.total = 0;
            this.count = 0;
            return val;
        }
    }

    class SumMetric extends Metric{
        float sum;

        public SumMetric(String metricId, int periodInSecs) {
            super(metricId, periodInSecs);
        }

        public SumMetric(String metricId, int periodInSecs, int accountId, String orgId) {
            super(metricId, periodInSecs, accountId, orgId);
        }

        @Override
        public void record(float val) {
            sum+= val;
        }

        @Override
        float getMetric() {
            return sum;
        }

        @Override
        MetricType getMetricType() {
            return MetricType.SUM;
        }

        @Override
        float getMetricAndReset() {
            float val = getMetric();
            this.sum = 0;
            return val;
        }
    }

    public static void sendDataToAkto(BasicDBList list){
        try {
            // Convert BasicDBList to List<MetricData>
            java.util.ArrayList<MetricData> metricDataList = new java.util.ArrayList<>();
            for (Object obj : list) {
                BasicDBObject metricsData = (BasicDBObject) obj;
                String metricId = metricsData.getString("metric_id");
                Object valObj = metricsData.get("val");
                float val = valObj instanceof Number ? ((Number) valObj).floatValue() : 0f;
                String orgId = metricsData.getString("org_id");
                String instanceId = metricsData.getString("instance_id");

                MetricData metricData = new MetricData(metricId, val, orgId, instanceId, MetricData.MetricType.SUM);
                metricDataList.add(metricData);
            }

            // Use DataActor to ingest metrics
            DataActorFactory.fetchInstance().ingestMetricData(metricDataList);
            loggerMaker.infoAndAddToDb("Updated traffic_metrics via DataActor");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while ingesting metrics: " + e.getMessage());
        }
    }
}
