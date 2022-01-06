package com.akto.runtime;

import com.akto.dao.KafkaHealthMetricsDao;
import com.akto.dto.KafkaHealthMetric;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class KafkaHealthMetricSyncTask implements Runnable{
    public Map<String,KafkaHealthMetric> kafkaHealthMetricsMap = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(KafkaHealthMetricSyncTask.class);
    @Override
    public void run() {
        try {
            logger.info("SYNCING");
            for (String key: kafkaHealthMetricsMap.keySet()) {
                KafkaHealthMetric kafkaHealthMetric = kafkaHealthMetricsMap.get(key);
                Bson filter = Filters.and(
                        Filters.eq(KafkaHealthMetric.TOPIC_NAME, kafkaHealthMetric.getTopicName()),
                        Filters.eq(KafkaHealthMetric.PARTITION, kafkaHealthMetric.getPartition())
                );

                ReplaceOptions replaceOptions = new ReplaceOptions();
                replaceOptions.upsert(true);

                KafkaHealthMetricsDao.instance.getMCollection().replaceOne(filter, kafkaHealthMetric, replaceOptions);
            }
            logger.info("SYNC DONE");
        } catch (Exception e) {
            logger.error("ERROR in kafka data sync from api runtime", e);
        }
    }
}
