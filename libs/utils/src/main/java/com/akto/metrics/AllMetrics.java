package com.akto.metrics;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.billing.Organization;
import com.akto.log.LoggerMaker;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import okhttp3.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AllMetrics {

    public static final DataActor dataActor = DataActorFactory.fetchInstance();
    private String instanceId;
    private String version;

	public void init(String instanceId, String version) {
        int accountId = Context.accountId.get();
        this.setInstanceId(instanceId);
        this.setVersion(version);

        Organization organization = DataActorFactory.fetchInstance().fetchOrganization(accountId);
        String orgId = organization.getId();

        runtimeKafkaRecordCount = new SumMetric("RT_KAFKA_RECORD_COUNT", 60, accountId, orgId);
        runtimeKafkaRecordSize = new SumMetric("RT_KAFKA_RECORD_SIZE", 60, accountId, orgId);
        runtimeProcessLatency = new LatencyMetric("RT_KAFKA_LATENCY", 60, accountId, orgId);
        postgreSampleDataInsertedCount = new SumMetric("PG_SAMPLE_DATA_INSERT_COUNT", 60, accountId, orgId);
        postgreSampleDataInsertLatency = new LatencyMetric("PG_SAMPLE_DATA_INSERT_LATENCY", 60, accountId, orgId);
        mergingJobLatency = new LatencyMetric("MERGING_JOB_LATENCY", 60, accountId, orgId);
        mergingJobUrlsUpdatedCount = new SumMetric("MERGING_JOB_URLS_UPDATED_COUNT", 60, accountId, orgId);
        staleSampleDataCleanupJobLatency = new LatencyMetric("STALE_SAMPLE_DATA_CLEANUP_JOB_LATENCY", 60, accountId, orgId);
        staleSampleDataDeletedCount = new SumMetric("STALE_SAMPLE_DATA_DELETED_COUNT", 60, accountId, orgId);
        mergingJobUrlUpdateLatency = new LatencyMetric("MERGING_JOB_URL_UPDATE_LATENCY", 60, accountId, orgId);
        cyborgCallLatency = new LatencyMetric("CYBORG_CALL_LATENCY", 60, accountId, orgId);
        cyborgCallCount = new SumMetric("CYBORG_CALL_COUNT", 60, accountId, orgId);
        cyborgDataSize = new SumMetric("CYBORG_DATA_SIZE", 60, accountId, orgId);
        testingRunCount = new SumMetric("TESTING_RUN_COUNT", 60, accountId, orgId);
        testingRunLatency = new LatencyMetric("TESTING_RUN_LATENCY", 60, accountId, orgId);
        totalSampleDataCount = new SumMetric("TOTAL_SAMPLE_DATA_COUNT", 60, accountId, orgId);
        sampleDataFetchLatency = new LatencyMetric("SAMPLE_DATA_FETCH_LATENCY", 60, accountId, orgId);
        sampleDataFetchCount = new SumMetric("SAMPLE_DATA_FETCH_COUNT", 60, accountId, orgId); // tODO: Do we need this?
        pgDataSizeInMb = new SumMetric("PG_DATA_SIZE_IN_MB", 60, accountId, orgId);
        kafkaOffset = new SumMetric("KAFKA_OFFSET", 60, accountId, orgId);
        kafkaRecordsLagMax = new SumMetric("KAFKA_RECORDS_LAG_MAX", 60, accountId, orgId);
        kafkaRecordsConsumedRate = new SumMetric("KAFKA_RECORDS_CONSUMED_RATE", 60, accountId, orgId);
        kafkaFetchAvgLatency = new LatencyMetric("KAFKA_FETCH_AVG_LATENCY", 60, accountId, orgId);
        kafkaBytesConsumedRate = new SumMetric("KAFKA_BYTES_CONSUMED_RATE", 60, accountId, orgId);
        cyborgNewApiCount = new SumMetric("CYBORG_NEW_API_COUNT", 60, accountId, orgId);
        cyborgTotalApiCount = new SumMetric("CYBORG_TOTAL_API_COUNT", 60, accountId, orgId);
        deltaCatalogTotalCount = new SumMetric("DELTA_CATALOG_TOTAL_COUNT", 60, accountId, orgId);
        deltaCatalogNewCount = new SumMetric("DELTA_CATALOG_NEW_COUNT", 60, accountId, orgId);
        cyborgApiPayloadSize = new SumMetric("CYBORG_API_PAYLOAD_SIZE", 60, accountId, orgId);
        multipleSampleDataFetchLatency = new LatencyMetric("MULTIPLE_SAMPLE_DATA_FETCH_LATENCY", 60, accountId, orgId);

        metrics = Arrays.asList(runtimeKafkaRecordCount, runtimeKafkaRecordSize, runtimeProcessLatency,
                postgreSampleDataInsertedCount, postgreSampleDataInsertLatency, mergingJobLatency, mergingJobUrlsUpdatedCount,
                staleSampleDataCleanupJobLatency, staleSampleDataDeletedCount, mergingJobUrlUpdateLatency, cyborgCallLatency,
                cyborgCallCount, cyborgDataSize, testingRunCount, testingRunLatency, totalSampleDataCount, sampleDataFetchLatency,
                sampleDataFetchCount, pgDataSizeInMb, kafkaOffset, kafkaRecordsLagMax, kafkaRecordsConsumedRate, kafkaFetchAvgLatency,
                kafkaBytesConsumedRate, cyborgNewApiCount, cyborgTotalApiCount, deltaCatalogNewCount, deltaCatalogTotalCount,
                cyborgApiPayloadSize, multipleSampleDataFetchLatency);

        if(executorService == null){
            executorService  = Executors.newScheduledThreadPool(1);
        }

        executorService.scheduleAtFixedRate(() -> {
            try {
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
                    metricsData.put("instance_id", this.getInstanceId());
                    metricsData.put("version", this.getVersion());
                    metricsData.put("account_id", m.accountId);
                    metricsData.put("timestamp", Context.now());
                    list.add(metricsData);

                }
                if(!list.isEmpty()) {
                    sendDataToAkto(list);
                    dataActor.insertRuntimeMetricsData(list);
                }
            } catch (Exception e){
                loggerMaker.errorAndAddToDb("Error while sending metrics to akto: " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
            }
        }, 0, 60, TimeUnit.SECONDS);
    }

    private AllMetrics(){}

    public static AllMetrics instance = new AllMetrics();

    private static final String URL = "https://logs.akto.io/ingest-metrics";

    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .writeTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .callTimeout(1, TimeUnit.SECONDS)
            .build();


    private final static LoggerMaker loggerMaker = new LoggerMaker(AllMetrics.class);

    private Metric runtimeKafkaRecordCount;
    private Metric runtimeKafkaRecordSize;
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
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(new BasicDBObject("data", list).toJson(), mediaType);
        Request request = new Request.Builder()
                .url(URL)
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .build();
        Response response = null;
        try {
            response =  client.newCall(request).execute();
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error while executing request " + request.url() + ": " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
        } finally {
            if (response != null) {
                response.close();
            }
        }
        if (response!= null && response.isSuccessful()) {
            loggerMaker.infoAndAddToDb("Updated traffic_metrics", LoggerMaker.LogDb.RUNTIME);
        } else {
            loggerMaker.infoAndAddToDb("Traffic_metrics not sent", LoggerMaker.LogDb.RUNTIME);
        }
    }

    public static void sendData(BasicDBList list){

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(new BasicDBObject("data", list).toJson(), mediaType);
        Request request = new Request.Builder()
                .url(URL)
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .build();
        Response response = null;
        try {
            response =  client.newCall(request).execute();
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error while executing request " + request.url() + ": " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
        } finally {
            if (response != null) {
                response.close();
            }
        }
        if (response!= null && response.isSuccessful()) {
            loggerMaker.infoAndAddToDb("Updated traffic_metrics", LoggerMaker.LogDb.RUNTIME);
        } else {
            loggerMaker.infoAndAddToDb("Traffic_metrics not sent", LoggerMaker.LogDb.RUNTIME);
        }
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
