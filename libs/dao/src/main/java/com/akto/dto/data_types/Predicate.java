package com.akto.dto.data_types;

import com.akto.dto.ApiInfo;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBObject;

import java.util.*;

public abstract class Predicate {
    private Type type;
    public static final String VALUE = "value";
    public static final String TYPE = "type";

    public enum Type {
        REGEX, STARTS_WITH, ENDS_WITH, IS_NUMBER, EQUALS_TO, CONTAINS, BELONGS_TO, NOT_BELONGS_TO
    }

    public Predicate(Type type) {
        this.type = type;
    }

    public abstract boolean validate(Object value);

    public Type getType() {
        return type;
    }

    public static Predicate generatePredicate(Predicate.Type type, Map<String,Object> valueMap) {
        if (valueMap == null || type == null) return null;
        Predicate predicate;
        String value;
        switch (type) {
            case REGEX:
                Object regex = valueMap.get(VALUE);
                if (!(regex instanceof String)) return null;
                value = regex.toString();
                if (value.length() == 0) return null;
                if (value.startsWith("/")) {
                    value = value.substring(1, value.lastIndexOf("/"));
                }
                predicate = new RegexPredicate(value);
                return predicate;
            case STARTS_WITH:
                Object prefix = valueMap.get(VALUE);
                if (!(prefix instanceof String)) return null;
                value = prefix.toString();
                if (value.length() == 0) return null;
                predicate = new StartsWithPredicate(value);
                return predicate;
            case ENDS_WITH:
                Object suffix = valueMap.get(VALUE);
                if (!(suffix instanceof String)) return null;
                value = suffix.toString();
                if (value.length() == 0) return null;
                predicate = new EndsWithPredicate(value);
                return predicate;
            case EQUALS_TO:
                Object v = valueMap.get(VALUE);
                if (!(v instanceof String)) return null;
                value = v.toString();
                if (value.length() == 0) return null;
                predicate = new EqualsToPredicate(value);
                return predicate;
            case IS_NUMBER:
                return new IsNumberPredicate();
            case CONTAINS:
                Object contain = valueMap.get(VALUE);
                if (! (contain instanceof String)) return null;
                return new ContainsPredicate((String) contain);
            case BELONGS_TO:
                Object belongsTo = valueMap.get(VALUE);
                Set<ApiInfo.ApiInfoKey> set = createApiInfoSetFromMap(belongsTo);
                if (set == null) return null;
                return new BelongsToPredicate(set);
            case NOT_BELONGS_TO:
                Object notBelongTo = valueMap.get(VALUE);
                Set<ApiInfo.ApiInfoKey> setNotBelong = createApiInfoSetFromMap(notBelongTo);
                return new NotBelongsToPredicate(setNotBelong);
            default:
                return null;
        }
    }

    private static Set<ApiInfo.ApiInfoKey> createApiInfoSetFromMap(Object value) {
        if (!(value instanceof List)) {
            return null;
        }
        HashSet<ApiInfo.ApiInfoKey> set = new HashSet<>();
        List listOfValues = (List) value;
        for (Object obj : listOfValues) {
            if (obj instanceof HashMap) {
                HashMap map = (HashMap) obj;
                BasicDBObject item = new BasicDBObject();
                item.putAll(map);
                ApiInfo.ApiInfoKey infoKey = new ApiInfo.ApiInfoKey(
                        item.getInt(ApiInfo.ApiInfoKey.API_COLLECTION_ID),
                        item.getString(ApiInfo.ApiInfoKey.URL),
                        URLMethods.Method.fromString(item.getString(ApiInfo.ApiInfoKey.METHOD)));
                set.add(infoKey);
            }
        }
        return set;
    }
    public void setType(Type type) {
        this.type = type;
    }
}
