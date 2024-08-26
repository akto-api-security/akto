package com.akto.utils.libs.utils.src.main.java.com.akto.runtime.policies;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.function.FailableFunction;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.traffic.SampleData;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.policies.ApiAccessTypePolicy;
import com.akto.utils.CalculateJob;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

public class ApiAccessTypePolicyUtil {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiAccessTypePolicyUtil.class, LogDb.DASHBOARD);

    public static void calcApiAccessType(ApiAccessTypePolicy policy, List<String> partnerIpList) {
        FailableFunction<ApiInfo, UpdateOneModel<ApiInfo>, Exception> func = (apiInfo) -> {
            SampleData sampleData = SampleDataDao.instance.fetchAllSampleDataForApi(
                    apiInfo.getId().getApiCollectionId(),
                    apiInfo.getId().getUrl(), apiInfo.getId().getMethod());
            if (sampleData != null && sampleData.getSamples() != null && !sampleData.getSamples().isEmpty()) {
                if (apiInfo.getApiAccessTypes() == null) {
                    apiInfo.setApiAccessTypes(new HashSet<>());
                }
                Set<ApiAccessType> oldApiAccessTypes = new HashSet<>(apiInfo.getApiAccessTypes());
                apiInfo.setApiAccessTypes(new HashSet<>());
                for (String sample : sampleData.getSamples()) {
                    try {
                        HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(sample);
                        policy.findApiAccessType(httpResponseParams, apiInfo, null, partnerIpList);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Unable to calculate access type for access type policy job for " + apiInfo.getId().toString());
                    }
                }

                /*
                 * If no change in access type ,
                 * return no update.
                 */
                if (oldApiAccessTypes.equals(apiInfo.getApiAccessTypes())) {
                    return null;
                }

                if (apiInfo.getApiAccessTypes() != null) {
                    return new UpdateOneModel<>(
                            ApiInfoDao.getFilter(apiInfo.getId()),
                            Updates.set(ApiInfo.API_ACCESS_TYPES, apiInfo.getApiAccessTypes()),
                            new UpdateOptions().upsert(false));
                }
            }
            return null;
        };
        int now = Context.now();
        loggerMaker.infoAndAddToDb(String.format("Starting update job for calcApiAccessType at %d", now));
        CalculateJob.apiInfoUpdateJob(func);
        int now2 = Context.now();
        int diff = now2 - now;
        loggerMaker.infoAndAddToDb(String.format("Finished update job for calcApiAccessType at %d , time taken : %d seconds", now2, diff));
    }

}
