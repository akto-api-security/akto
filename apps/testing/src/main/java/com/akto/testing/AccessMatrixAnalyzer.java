package com.akto.testing;

import java.util.ArrayList;
import java.util.List;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.AccessMatrixTaskInfosDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.AccessMatrixTaskInfo;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.type.URLMethods.Method;
import com.akto.store.SampleMessageStore;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccessMatrixAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(AccessMatrixAnalyzer.class);

    public List<ApiInfoKey> getEndpointsToAnalyze(AccessMatrixTaskInfo task) {
        final List<ApiInfoKey> endpoints = new ArrayList<>();
        if(task.getApiInfoKeys() == null || task.getApiInfoKeys().isEmpty()) {
            List<BasicDBObject> list = ApiCollectionsDao.fetchEndpointsInCollectionUsingHost(task.getApiCollectionId(), 0, 10_000, 60 * 30 * 24 * 30);

            if (list != null && !list.isEmpty()) {
                list.forEach(element -> {
                    BasicDBObject item = (BasicDBObject) element.get(Constants.ID);
                    if (item == null) {
                        return;
                    }
                    ApiInfoKey apiInfoKey = new ApiInfoKey(
                            task.getApiCollectionId(),
                            item.getString(ApiInfoKey.URL),
                            Method.fromString(item.getString(ApiInfoKey.METHOD)));
                    endpoints.add(apiInfoKey);
                });
            }

        } else {
            endpoints.addAll(task.getApiInfoKeys());
        }

        return endpoints;
    }

    public void run() {
        Context.accountId.set(1_000_000);

        Bson pendingTasks = Filters.lt(AccessMatrixTaskInfo.NEXT_SCHEDULED_TIMESTAMP, Context.now());
        for(AccessMatrixTaskInfo task: AccessMatrixTaskInfosDao.instance.findAll(pendingTasks)) {
            logger.info("Running task: " + task.toString());

            List<ApiInfoKey> endpoints = getEndpointsToAnalyze(task);
            logger.info("Number of endpoints: " + (endpoints == null ? 0 : endpoints.size()));

            List<TestRoles> testRoles = TestRolesDao.instance.findAll(new BasicDBObject()); 

            
        }
    }
}
