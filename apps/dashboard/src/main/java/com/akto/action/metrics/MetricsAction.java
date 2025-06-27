package com.akto.action.metrics;

import com.akto.action.UserAction;
import com.akto.dao.metrics.MetricDataDao;
import com.akto.dto.metrics.MetricData;
import com.akto.dao.common.LoggerMaker;
import com.akto.dao.common.LoggerMaker.LogDb;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricsAction extends UserAction {
    private static final LoggerMaker loggerMaker = new LoggerMaker(MetricsAction.class);
    private String metricId;
    private long startTime;
    private long endTime;
    private Map<String, Object> result = new HashMap<>();

    public String getMetrics() {
        try {
            List<MetricData> metrics;
            metrics = MetricDataDao.instance.getMetricsForTimeRange(startTime, endTime);
            List<Map<String, Object>> metricsData = new ArrayList<>();
            for (MetricData metric : metrics) {
                Map<String, Object> metricMap = new HashMap<>();
                metricMap.put("metricId", metric.getMetricId());
                metricMap.put("value", metric.getValue());
                metricMap.put("timestamp", metric.getTimestamp());
                metricMap.put("instanceId", metric.getInstanceId());
                metricsData.add(metricMap);
            }

            result.put("metrics", metricsData);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching metrics: " + e.getMessage(), LogDb.DASHBOARD);
            result.put("error", "Error fetching metrics: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    public String getMetricId() {
        return metricId;
    }

    public void setMetricId(String metricId) {
        this.metricId = metricId;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }
} 