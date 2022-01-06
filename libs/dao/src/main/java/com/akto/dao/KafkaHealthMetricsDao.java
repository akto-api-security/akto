package com.akto.dao;

import com.akto.dto.KafkaHealthMetric;

public class KafkaHealthMetricsDao extends CommonContextDao<KafkaHealthMetric> {

    public static final KafkaHealthMetricsDao instance = new KafkaHealthMetricsDao();

    @Override
    public String getCollName() {
        return "kafka_health_metrics";
    }

    @Override
    public Class<KafkaHealthMetric> getClassT() {
        return KafkaHealthMetric.class;
    }
}
