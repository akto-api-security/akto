package com.akto.action.observe;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import org.bson.conversions.Bson;

import java.util.List;

public class Utils {

    public final static int DELTA_PERIOD_VALUE = 60 * 24 * 60 * 60;
    public static final int LIMIT = 2000;

    public static List<BasicDBObject> fetchEndpointsInCollectionUsingHost(int apiCollectionId, int skip) {

        return ApiCollectionsDao.fetchEndpointsInCollectionUsingHost(apiCollectionId, skip, LIMIT, DELTA_PERIOD_VALUE);
    }

    public final static String _START_TS = "startTs";
    public final static String _LAST_SEEN_TS = "lastSeenTs";

    public static List<BasicDBObject> fetchEndpointsInCollection(int apiCollectionId, int skip) {
        return ApiCollectionsDao.fetchEndpointsInCollection(apiCollectionId, skip, LIMIT, DELTA_PERIOD_VALUE);
    }

    public static List<SingleTypeInfo> fetchHostSTI(int apiCollectionId, int skip) {
        Bson filterQ = SingleTypeInfoDao.filterForHostHeader(apiCollectionId, true);
        return SingleTypeInfoDao.instance.findAll(filterQ, skip,10_000, null);
    }

    public static String escapeSpecialCharacters(String inputString){
        String specialChars = "\\.*+?^${}()|[]";
        StringBuilder escaped = new StringBuilder();
        
        for (char c : inputString.toCharArray()) {
            if (specialChars.contains(String.valueOf(c))) {
                // Escape special character
                escaped.append("\\").append(c);
            } else {
                escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
