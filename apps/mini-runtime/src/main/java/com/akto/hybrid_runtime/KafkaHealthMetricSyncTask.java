package com.akto.hybrid_runtime;

import com.akto.dao.KafkaHealthMetricsDao;
import com.akto.dao.context.Context;
import com.akto.dto.KafkaHealthMetric;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class KafkaHealthMetricSyncTask implements Runnable{
    Consumer<String, String>  consumer;
    public Map<String,KafkaHealthMetric> kafkaHealthMetricsMap = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(KafkaHealthMetricSyncTask.class);
    private static final LoggerMaker loggerMaker = new LoggerMaker(APICatalogSync.class);


    public KafkaHealthMetricSyncTask(Consumer<String, String>  consumer) {
        this.consumer = consumer;
    }


    @Override
    public void run() {
        try {
            logger.info("SYNCING");
            for (TopicPartition tp: consumer.assignment()) {
                String tpName = tp.topic();
                long position = consumer.position(tp);
                long endOffset = consumer.endOffsets(Collections.singleton(tp)).get(tp);
                int partition = tp.partition();

                KafkaHealthMetric kafkaHealthMetric = new KafkaHealthMetric(tpName, partition,
                        position,endOffset,Context.now());
                kafkaHealthMetricsMap.put(kafkaHealthMetric.hashCode()+"", kafkaHealthMetric);
            }

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
            loggerMaker.errorAndAddToDb("ERROR in kafka data sync from api runtime" + e, LogDb.RUNTIME);
        }
    }
}
