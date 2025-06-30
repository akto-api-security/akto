package com.akto.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.type.EndpointInfo;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.SingleTypeInfo.SubType;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayloadAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(PayloadAnalyzer.class);
    private static final LoggerMaker loggerMaker = new LoggerMaker(PayloadAnalyzer.class);
    private static EndpointInfo endpointInfo = null;

    public static EndpointInfo getEndpointInfo() {
        return endpointInfo;
    }

    public static void init(int accountId) {
        if (endpointInfo == null) {
            synchronized(PayloadAnalyzer.class) {
                if (endpointInfo == null) {
                    endpointInfo = new EndpointInfo(new HashMap<>());
                }
            }
        }

        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(new Runnable(){

            @Override
            public void run() {
                Context.accountId.set(accountId);
                EndpointInfo temp = endpointInfo;
                endpointInfo = new EndpointInfo(new HashMap<>());
                logger.info("gathering updates");
                ArrayList<WriteModel<SingleTypeInfo>> bulkUpdates = new ArrayList<>();

                for(Map<String, RequestTemplate> methodToTemplate: temp.getAllEndpoints().values()) {

                    for(RequestTemplate template: methodToTemplate.values()) {

                        Set<String> paramNames = template.getParameters().keySet();

                        for(String paramName: paramNames) {
                            Map<SubType, SingleTypeInfo> occurrences = template.getParameters().get(paramName).getOccurrences();
                            for(SubType subType: occurrences.keySet()) {
                                SingleTypeInfo info = occurrences.get(subType);
                                Bson update = Updates.inc("count", info.getCount());

                                Bson updateKey = SingleTypeInfoDao.createFilters(info);
                                update = Updates.combine(update,
                                Updates.setOnInsert(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(info.getApiCollectionId())));

                                bulkUpdates.add(new UpdateOneModel<>(updateKey, update, new UpdateOptions().upsert(true)));

                            }
                        }
                    }

                    try {    
                        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkUpdates);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e.toString(), LogDb.RUNTIME);
                    }
                }
                logger.info("updates completed");
            }
            
        }, 5, 10, TimeUnit.SECONDS); 

        logger.info("update service scheduled");
    }
}
