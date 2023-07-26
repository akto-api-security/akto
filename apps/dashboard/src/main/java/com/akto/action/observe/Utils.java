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

    public static List<BasicDBObject> fetchEndpointsInCollectionUsingHost(int apiCollectionId, int skip) {

        return ApiCollectionsDao.fetchEndpointsInCollectionUsingHost(apiCollectionId, skip, LIMIT, DELTA_PERIOD_VALUE);
    }

    public static List<BasicDBObject> fetchEndpointsInCollection(int apiCollectionId, int skip) {
        return ApiCollectionsDao.fetchEndpointsInCollection(apiCollectionId, skip, LIMIT, DELTA_PERIOD_VALUE);
    }

    public static List<SingleTypeInfo> fetchHostSTI(int apiCollectionId, int skip) {
        Bson filterQ = SingleTypeInfoDao.filterForHostHeader(apiCollectionId, true);
        return SingleTypeInfoDao.instance.findAll(filterQ, skip,10_000, null);
    }
}
