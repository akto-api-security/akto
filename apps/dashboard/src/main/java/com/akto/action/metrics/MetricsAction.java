package com.akto.action.metrics;

import com.akto.action.UserAction;
import com.akto.dao.metrics.MetricDataDao;
import com.akto.dto.metrics.MetricData;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.*;

public class MetricsAction extends UserAction {
    private static final LoggerMaker loggerMaker = new LoggerMaker(MetricsAction.class);
    private long startTime;
    private long endTime;
    private Map<String, Object> result = new HashMap<>();
    private List<MetricData.Name> names;

    public String fetchAllMetricsDesciptions(){
        names = Arrays.asList(MetricData.Name.values());
        return SUCCESS.toUpperCase();
    }


    public String getMetrics() {
        try {
            List<MetricData> metrics;
            metrics = MetricDataDao.instance.getMetricsForTimeRange(startTime, endTime);
            if (metrics == null) {
                result.put("metrics", new ArrayList<>());
                return SUCCESS.toUpperCase();
            }
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

    public List<MetricData.Name> getNames() {
        return names;
    }

    public void setNames(List<MetricData.Name> names) {
        this.names = names;
    }
}