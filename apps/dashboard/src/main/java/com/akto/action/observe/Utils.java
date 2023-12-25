package com.akto.action.observe;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

public class Utils {

    public final static int DELTA_PERIOD_VALUE = 60 * 24 * 60 * 60;
    public static final int LIMIT = 2000;
    public static final int LARGE_LIMIT = 10_000;

    public static List<BasicDBObject> fetchEndpointsInCollectionUsingHost(int apiCollectionId, int skip) {

        return ApiCollectionsDao.fetchEndpointsInCollectionUsingHost(apiCollectionId, skip, LIMIT, DELTA_PERIOD_VALUE);
    }

    public final static String _START_TS = "startTs";
    public final static String _LAST_SEEN_TS = "lastSeenTs";

    public static int countEndpoints(Bson filters) {
        int ret = 0;

        List<Bson> pipeline = new ArrayList<>();
        BasicDBObject groupedId = new BasicDBObject("apiCollectionId", "$apiCollectionId")
                .append("url", "$url")
                .append("method", "$method");

        Bson projections = Projections.fields(
                Projections.include("timestamp", "lastSeen", "apiCollectionId", "url", "method"));

        pipeline.add(Aggregates.project(projections));
        pipeline.add(Aggregates.match(filters));
        pipeline.add(Aggregates.group(groupedId));
        pipeline.add(Aggregates.limit(LARGE_LIMIT));
        pipeline.add(Aggregates.count());

        MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).cursor();

        while (endpointsCursor.hasNext()) {
            ret = endpointsCursor.next().getInt("count");
            break;
        }

        return ret;
    }

    public static List<BasicDBObject> fetchEndpointsInCollection(int apiCollectionId, int skip) {
        return ApiCollectionsDao.fetchEndpointsInCollection(apiCollectionId, skip, LIMIT, DELTA_PERIOD_VALUE);
    }

    public static List<SingleTypeInfo> fetchHostSTI(int apiCollectionId, int skip) {
        Bson filterQ = SingleTypeInfoDao.filterForHostHeader(apiCollectionId, true);
        return SingleTypeInfoDao.instance.findAll(filterQ, skip,10_000, null);
    }
}
