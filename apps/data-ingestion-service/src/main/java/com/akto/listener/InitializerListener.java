package com.akto.listener;

import javax.servlet.ServletContextListener;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.metrics.AllMetrics;
import com.akto.metrics.ModuleInfoWorker;
import com.akto.utils.KafkaUtils;


public class InitializerListener implements ServletContextListener {

    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {
        KafkaUtils kafkaUtils = new KafkaUtils();
        kafkaUtils.initKafkaProducer();

        try {
            DataActor dataActor = DataActorFactory.fetchInstance();
            ModuleInfoWorker.init(ModuleInfo.ModuleType.DATA_INGESTION, dataActor);

            int accountId = com.akto.action.IngestionAction.getAccountId();
            AllMetrics.instance.initDataIngestion(accountId);
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    @Override
    public void contextDestroyed(javax.servlet.ServletContextEvent sce) {
        // override
    }

}
