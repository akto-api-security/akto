package com.akto.dto.reports;

import com.akto.dto.messaging.InboxParams.Source;

public class MetricReportElement extends ReportElement {

    int metricId;

    public MetricReportElement(int metricId) {
        this.metricId = metricId;
        this.source = Source.Metric;
    }

    public int getMetricId() {
        return metricId;
    }

    public void setMetricId(int metricId) {
        this.metricId = metricId;
    }
}
