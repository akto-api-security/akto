package com.akto.runtime;

import com.akto.dao.KafkaHealthMetricsDao;
import com.akto.dto.KafkaHealthMetric;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.conversions.Bson;

import java.util.HashMap;
import java.util.Map;

public class KafkaHealthMetricSyncTask implements Runnable{
    public Map<String,KafkaHealthMetric> kafkaHealthMetricsMap = new HashMap<>();
    @Override
    public void run() {
        System.out.println("SYNCING");
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
        System.out.println("SYNC DONE");
    }
}
