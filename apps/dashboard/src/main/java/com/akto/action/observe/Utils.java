package com.akto.action.observe;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import org.bson.conversions.Bson;

import java.util.List;

public class Utils {

    public final static int DELTA_PERIOD_VALUE = 60 * 24 * 60 * 60;
    public static final int LIMIT = -1;

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

    public static boolean isInputSanitized(String value, StringBuilder error, int maxValueLength){
        if (value == null || value.isEmpty()) {
            error.append("Value cannot be null or empty");
            return false;
        }

        if (value.length() > maxValueLength) {
            error.append("Value cannot be greater than ").append(maxValueLength).append(" characters");
            return false;
        }

        for (char c : value.toCharArray()) {
            boolean alphabets = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
            boolean numbers = c >= '0' && c <= '9';
            boolean specialChars = c == '-' || c == '.' || c == '_' || c == '/' || c == '+';
            boolean spaces = c == ' ';

            if (!(alphabets || numbers || specialChars || spaces)) {
                error.append("Value names can only be alphanumeric and contain '-','.','_','/' and '_'");
                return false;

            }
        }
        return true;
    }
}
