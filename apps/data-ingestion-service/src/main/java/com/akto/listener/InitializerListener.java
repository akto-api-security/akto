package com.akto.listener;

import javax.servlet.ServletContextListener;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.metrics.ModuleInfoWorker;
import com.akto.utils.KafkaUtils;


public class InitializerListener implements ServletContextListener {

    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {
        KafkaUtils kafkaUtils = new KafkaUtils();
        kafkaUtils.initKafkaProducer();

        DataActor dataActor = DataActorFactory.fetchInstance();
        ModuleInfoWorker.init(ModuleInfo.ModuleType.DATA_INGESTION, dataActor);
    }

    @Override
    public void contextDestroyed(javax.servlet.ServletContextEvent sce) {
        // override
    }

}
