package com.akto.dependency_analyser;

import com.akto.dao.DependencyNodeDao;
import com.akto.dto.DependencyNode;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DependencyAnalyserUtils {

    public static void syncWithDb(List<DependencyNode> nodes) {
        ArrayList<WriteModel<DependencyNode>> bulkUpdates1 = new ArrayList<>();
        ArrayList<WriteModel<DependencyNode>> bulkUpdates2 = new ArrayList<>();
        ArrayList<WriteModel<DependencyNode>> bulkUpdates3 = new ArrayList<>();

        for (DependencyNode dependencyNode: nodes) {

            String urlResp = dependencyNode.getUrlResp();
            String apiCollectionIdResp = dependencyNode.getApiCollectionIdResp();
            String methodResp = dependencyNode.getMethodResp();

            String urlReq = dependencyNode.getUrlReq();
            String apiCollectionIdReq = dependencyNode.getApiCollectionIdReq();
            String methodReq = dependencyNode.getMethodReq();

            if (apiCollectionIdResp.equals(apiCollectionIdReq) && urlResp.equals(urlReq) && methodResp.equals(methodReq)) continue;

            for (DependencyNode.ParamInfo paramInfo: dependencyNode.getParamInfos()) {
                Bson filter1 = Filters.and(
                        Filters.eq(DependencyNode.API_COLLECTION_ID_REQ, apiCollectionIdReq),
                        Filters.eq(DependencyNode.URL_REQ, urlReq),
                        Filters.eq(DependencyNode.METHOD_REQ, methodReq),
                        Filters.eq(DependencyNode.API_COLLECTION_ID_RESP, apiCollectionIdResp),
                        Filters.eq(DependencyNode.URL_RESP, urlResp),
                        Filters.eq(DependencyNode.METHOD_RESP, methodResp)
                );

                Bson update1 = Updates.push(DependencyNode.PARAM_INFOS, new Document("$each", Collections.emptyList()));

                // this update is to make sure the document exist else create new one
                UpdateOneModel<DependencyNode> updateOneModel1 = new UpdateOneModel<DependencyNode>(
                        filter1, update1, new UpdateOptions().upsert(true)
                );


                Bson filter2 = Filters.and(
                        Filters.eq(DependencyNode.API_COLLECTION_ID_REQ, apiCollectionIdReq),
                        Filters.eq(DependencyNode.URL_REQ, urlReq),
                        Filters.eq(DependencyNode.METHOD_REQ, methodReq),
                        Filters.eq(DependencyNode.API_COLLECTION_ID_RESP, apiCollectionIdResp),
                        Filters.eq(DependencyNode.URL_RESP, urlResp),
                        Filters.eq(DependencyNode.METHOD_RESP, methodResp),
                        Filters.not(Filters.elemMatch(DependencyNode.PARAM_INFOS,
                                Filters.and(
                                        Filters.eq(DependencyNode.ParamInfo.REQUEST_PARAM, paramInfo.getRequestParam()),
                                        Filters.eq(DependencyNode.ParamInfo.RESPONSE_PARAM, paramInfo.getResponseParam()),
                                        Filters.eq(DependencyNode.ParamInfo.IS_URL_PARAM, paramInfo.isUrlParam()),
                                        Filters.eq(DependencyNode.ParamInfo.IS_HEADER, paramInfo.isHeader())
                                )
                        ))
                );

                Bson update2 = Updates.push(DependencyNode.PARAM_INFOS,
                        new BasicDBObject(DependencyNode.ParamInfo.REQUEST_PARAM, paramInfo.getRequestParam())
                                .append(DependencyNode.ParamInfo.RESPONSE_PARAM, paramInfo.getResponseParam())
                                .append(DependencyNode.ParamInfo.IS_URL_PARAM, paramInfo.isUrlParam())
                                .append(DependencyNode.ParamInfo.IS_HEADER, paramInfo.isHeader())
                                .append(DependencyNode.ParamInfo.COUNT, 0)
                );

                // this update is to add paramInfo if it doesn't exist. If exists nothing happens
                UpdateOneModel<DependencyNode> updateOneModel2 = new UpdateOneModel<>(
                        filter2, update2, new UpdateOptions().upsert(false)
                );

                Bson filter3 = Filters.and(
                        Filters.eq(DependencyNode.API_COLLECTION_ID_REQ, apiCollectionIdReq),
                        Filters.eq(DependencyNode.URL_REQ, urlReq),
                        Filters.eq(DependencyNode.METHOD_REQ, methodReq),
                        Filters.eq(DependencyNode.API_COLLECTION_ID_RESP, apiCollectionIdResp),
                        Filters.eq(DependencyNode.URL_RESP, urlResp),
                        Filters.eq(DependencyNode.METHOD_RESP, methodResp),
                        Filters.eq(DependencyNode.PARAM_INFOS + "." + DependencyNode.ParamInfo.REQUEST_PARAM, paramInfo.getRequestParam()),
                        Filters.eq(DependencyNode.PARAM_INFOS + "." + DependencyNode.ParamInfo.RESPONSE_PARAM, paramInfo.getResponseParam()),
                        Filters.eq(DependencyNode.PARAM_INFOS + "." + DependencyNode.ParamInfo.IS_URL_PARAM, paramInfo.isUrlParam()),
                        Filters.eq(DependencyNode.PARAM_INFOS + "." + DependencyNode.ParamInfo.IS_HEADER, paramInfo.isHeader())
                );

                Bson update3 = Updates.combine(
                        Updates.inc(DependencyNode.PARAM_INFOS + ".$." + DependencyNode.ParamInfo.COUNT, paramInfo.getCount()),
                        Updates.set(DependencyNode.LAST_UPDATED, dependencyNode.getLastUpdated())
                );

                // this update runs everytime to update the count
                UpdateOneModel<DependencyNode> updateOneModel3 = new UpdateOneModel<>(
                        filter3, update3,  new UpdateOptions().upsert(false)
                );

                bulkUpdates1.add(updateOneModel1);
                bulkUpdates2.add(updateOneModel2);
                bulkUpdates3.add(updateOneModel3);
            }
        }

        // ordered has to be true or else won't work
        if (bulkUpdates1.size() > 0) DependencyNodeDao.instance.getMCollection().bulkWrite(bulkUpdates1, new BulkWriteOptions().ordered(false));
        if (bulkUpdates2.size() > 0) DependencyNodeDao.instance.getMCollection().bulkWrite(bulkUpdates2, new BulkWriteOptions().ordered(false));
        if (bulkUpdates3.size() > 0) DependencyNodeDao.instance.getMCollection().bulkWrite(bulkUpdates3, new BulkWriteOptions().ordered(false));
    }

}
