package com.akto.metrics;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.dto.billing.Organization;
import com.akto.dto.metrics.MetricData;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.OrgUtils;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import com.sun.management.OperatingSystemMXBean;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AllMetrics {

    private String instance_id;

    public void init(LogDb module, boolean pgMetrics, DataActor dataActor, int accountId, String instanceId) {
        this.dataActor = dataActor;
        this.instance_id = instanceId;

        Organization organization = OrgUtils.getOrganizationCached(accountId);
        String orgId = organization.getId();

        if(LogDb.RUNTIME.equals(module)){
            runtimeKafkaRecordCount = new SumMetric("RT_KAFKA_RECORD_COUNT", 60, accountId, orgId);
            runtimeKafkaRecordSize = new SumMetric("RT_KAFKA_RECORD_SIZE", 60, accountId, orgId);
            runtimeProcessLatency = new LatencyMetric("RT_KAFKA_LATENCY", 60, accountId, orgId);
            runtimeApiReceivedCount = new SumMetric("RT_API_RECEIVED_COUNT", 60, accountId, orgId);
            kafkaRecordsLagMax = new MaxMetric("KAFKA_RECORDS_LAG_MAX", 60, accountId, orgId);
            kafkaRecordsConsumedRate = new GaugeMetric("KAFKA_RECORDS_CONSUMED_RATE", 60, accountId, orgId);
            kafkaFetchAvgLatency = new GaugeMetric("KAFKA_FETCH_AVG_LATENCY", 60, accountId, orgId);
            kafkaBytesConsumedRate = new GaugeMetric("KAFKA_BYTES_CONSUMED_RATE", 60, accountId, orgId);
            cyborgNewApiCount = new SumMetric("CYBORG_NEW_API_COUNT", 60, accountId, orgId);
            cyborgTotalApiCount = new SumMetric("CYBORG_TOTAL_API_COUNT", 60, accountId, orgId);
            deltaCatalogTotalCount = new SumMetric("DELTA_CATALOG_TOTAL_COUNT", 60, accountId, orgId);
            deltaCatalogNewCount = new SumMetric("DELTA_CATALOG_NEW_COUNT", 60, accountId, orgId);
            cyborgApiPayloadSize = new SumMetric("CYBORG_API_PAYLOAD_SIZE", 60, accountId, orgId);
        }

        if(pgMetrics){
            postgreSampleDataInsertedCount = new SumMetric("PG_SAMPLE_DATA_INSERT_COUNT", 60, accountId, orgId);
            postgreSampleDataInsertLatency = new LatencyMetric("PG_SAMPLE_DATA_INSERT_LATENCY", 60, accountId, orgId);
            mergingJobLatency = new LatencyMetric("MERGING_JOB_LATENCY", 60, accountId, orgId);
            mergingJobUrlsUpdatedCount = new SumMetric("MERGING_JOB_URLS_UPDATED_COUNT", 60, accountId, orgId);
            staleSampleDataCleanupJobLatency = new LatencyMetric("STALE_SAMPLE_DATA_CLEANUP_JOB_LATENCY", 60, accountId, orgId);
            staleSampleDataDeletedCount = new SumMetric("STALE_SAMPLE_DATA_DELETED_COUNT", 60, accountId, orgId);
            mergingJobUrlUpdateLatency = new LatencyMetric("MERGING_JOB_URL_UPDATE_LATENCY", 60, accountId, orgId);
            totalSampleDataCount = new SumMetric("TOTAL_SAMPLE_DATA_COUNT", 60, accountId, orgId);
            pgDataSizeInMb = new SumMetric("PG_DATA_SIZE_IN_MB", 60, accountId, orgId);
        }

        if(LogDb.TESTING.equals(module)){
            testingRunCount = new SumMetric("TESTING_RUN_COUNT", 60, accountId, orgId);
            testingRunLatency = new LatencyMetric("TESTING_RUN_LATENCY", 60, accountId, orgId);
            sampleDataFetchLatency = new LatencyMetric("SAMPLE_DATA_FETCH_LATENCY", 60, accountId, orgId);
            multipleSampleDataFetchLatency = new LatencyMetric("MULTIPLE_SAMPLE_DATA_FETCH_LATENCY", 60, accountId, orgId);
        }

        // sampleDataFetchCount = new SumMetric("SAMPLE_DATA_FETCH_COUNT", 60, accountId, orgId); // tODO: Do we need this?
        // kafkaOffset = new SumMetric("KAFKA_OFFSET", 60, accountId, orgId);
        cyborgCallLatency = new LatencyMetric("CYBORG_CALL_LATENCY", 60, accountId, orgId);
        cyborgCallCount = new SumMetric("CYBORG_CALL_COUNT", 60, accountId, orgId);
        cyborgDataSize = new SumMetric("CYBORG_DATA_SIZE", 60, accountId, orgId);

        // Infrastructure metrics - always initialized
        cpuUsagePercent = new GaugeMetric("CPU_USAGE_PERCENT", 60, accountId, orgId);
        heapMemoryUsedMb = new GaugeMetric("HEAP_MEMORY_USED_MB", 60, accountId, orgId);
        heapMemoryMaxMb = new GaugeMetric("HEAP_MEMORY_MAX_MB", 60, accountId, orgId);
        nonHeapMemoryUsedMb = new GaugeMetric("NON_HEAP_MEMORY_USED_MB", 60, accountId, orgId);
        threadCount = new GaugeMetric("THREAD_COUNT", 60, accountId, orgId);
        availableProcessors = new GaugeMetric("AVAILABLE_PROCESSORS", 60, accountId, orgId);
        totalPhysicalMemoryMb = new GaugeMetric("TOTAL_PHYSICAL_MEMORY_MB", 60, accountId, orgId);

        // Any new metric needs to be added here as well.
        metrics = Arrays.asList(runtimeKafkaRecordCount, runtimeKafkaRecordSize, runtimeProcessLatency,
                postgreSampleDataInsertedCount, postgreSampleDataInsertLatency, mergingJobLatency, mergingJobUrlsUpdatedCount,
                staleSampleDataCleanupJobLatency, staleSampleDataDeletedCount, mergingJobUrlUpdateLatency, cyborgCallLatency,
                cyborgCallCount, cyborgDataSize, testingRunCount, testingRunLatency, totalSampleDataCount, sampleDataFetchLatency,
                pgDataSizeInMb, kafkaRecordsLagMax, kafkaRecordsConsumedRate, kafkaFetchAvgLatency,
                kafkaBytesConsumedRate, cyborgNewApiCount, cyborgTotalApiCount, deltaCatalogNewCount, deltaCatalogTotalCount,
                cyborgApiPayloadSize, multipleSampleDataFetchLatency, runtimeApiReceivedCount,
                cpuUsagePercent, heapMemoryUsedMb, heapMemoryMaxMb, nonHeapMemoryUsedMb, threadCount,
                availableProcessors, totalPhysicalMemoryMb);

        AllMetrics _this = this;
        executorService.scheduleWithFixedDelay(() -> {
            try {
                Context.accountId.set(accountId);

                // Collect infrastructure metrics from MXBeans
                collectInfraMetrics();

                BasicDBList list = new BasicDBList();
                List<MetricData> metricDataList = new ArrayList<>();
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
                    MetricData.MetricType type = MetricData.MetricType.SUM;
                    switch (m.getMetricType()) {
                        case SUM:
                            type = MetricData.MetricType.SUM;
                            break;
                        case LATENCY:
                            type = MetricData.MetricType.LATENCY;
                            break;
                        case MAX:
                            type = MetricData.MetricType.MAX;
                            break;
                        case GAUGE:
                            type = MetricData.MetricType.GAUGE;
                            break;
                    }
                    MetricData metricData = new MetricData(
                            m.metricId,
                            metric,
                            m.orgId,
                            instance_id,
                            type
                    );
                    metricDataList.add(metricData);
                }
                if(!list.isEmpty()) {
                    _this.sendDataToAkto(list, metricDataList);
                }
            } catch (Exception e){
                loggerMaker.errorAndAddToDb("Error while sending metrics to akto: " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
            }
        }, 0, 120, TimeUnit.SECONDS);
    }

    private AllMetrics(){}

    public static AllMetrics instance = new AllMetrics();

    private final static LoggerMaker loggerMaker = new LoggerMaker(AllMetrics.class, LogDb.RUNTIME);
    private DataActor dataActor;
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
    private Metric runtimeApiReceivedCount = null;

    // Infrastructure metrics (CPU, Memory, Threads)
    private Metric cpuUsagePercent = null;
    private Metric heapMemoryUsedMb = null;
    private Metric heapMemoryMaxMb = null;
    private Metric nonHeapMemoryUsedMb = null;
    private Metric threadCount = null;
    private Metric availableProcessors = null;
    private Metric totalPhysicalMemoryMb = null;

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

    public void setRuntimeApiReceivedCount(float val){
        if(runtimeApiReceivedCount != null)
            runtimeApiReceivedCount.record(val);
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


    private static final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    public enum MetricType{
        LATENCY, SUM, MAX, GAUGE
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

    class MaxMetric extends Metric{
        float max;

        public MaxMetric(String metricId, int periodInSecs) {
            super(metricId, periodInSecs);
        }

        public MaxMetric(String metricId, int periodInSecs, int accountId, String orgId) {
            super(metricId, periodInSecs, accountId, orgId);
        }

        @Override
        public void record(float val) {
            if(val > max) {
                max = val;
            }
        }

        @Override
        float getMetric() {
            return max;
        }

        @Override
        MetricType getMetricType() {
            return MetricType.MAX;
        }

        @Override
        float getMetricAndReset() {
            float val = getMetric();
            this.max = 0;
            return val;
        }
    }

    class GaugeMetric extends Metric{
        float value;

        public GaugeMetric(String metricId, int periodInSecs) {
            super(metricId, periodInSecs);
        }

        public GaugeMetric(String metricId, int periodInSecs, int accountId, String orgId) {
            super(metricId, periodInSecs, accountId, orgId);
        }

        @Override
        public void record(float val) {
            this.value = val;
        }

        @Override
        float getMetric() {
            return value;
        }

        @Override
        MetricType getMetricType() {
            return MetricType.GAUGE;
        }

        @Override
        float getMetricAndReset() {
            float val = getMetric();
            this.value = 0;
            return val;
        }
    }

    private void collectInfraMetrics() {
        try {
            // CPU metrics
            OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double cpuLoad = osBean.getProcessCpuLoad();
            if (cpuLoad >= 0) { // -1 means not available
                cpuUsagePercent.record((float) (cpuLoad * 100));
            }

            // Memory metrics
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

            float heapUsedMb = heapUsage.getUsed() / (1024f * 1024f);
            float heapMaxMb = heapUsage.getMax() / (1024f * 1024f);
            float nonHeapUsedMb = nonHeapUsage.getUsed() / (1024f * 1024f);

            heapMemoryUsedMb.record(heapUsedMb);
            heapMemoryMaxMb.record(heapMaxMb);
            nonHeapMemoryUsedMb.record(nonHeapUsedMb);

            // Thread metrics
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            threadCount.record(threadBean.getThreadCount());

            // Available processors (container-aware in Java 8u191+/10+)
            availableProcessors.record(Runtime.getRuntime().availableProcessors());

            // Total physical memory (container-aware)
            long totalMemory = osBean.getTotalPhysicalMemorySize();
            if (totalMemory > 0) {
                totalPhysicalMemoryMb.record(totalMemory / (1024f * 1024f));
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error collecting infra metrics: " + e.getMessage(), LogDb.RUNTIME);
        }
    }

    private void sendDataToAkto(BasicDBList list,List<MetricData> metricDataList){
        try {
            dataActor.ingestMetricData(metricDataList);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while executing request " + e.getMessage());
        }
    }
}
