package com.akto.dto.data_types;

import com.akto.dto.ApiInfo;
import com.mongodb.BasicDBObject;

import java.util.List;
import java.util.Map;

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
                if (! (belongsTo instanceof List)) return null;

                return new BelongsToPredicate((List<BasicDBObject>) belongsTo);
            case NOT_BELONGS_TO:
                Object notBelongTo = valueMap.get(VALUE);
                if (! (notBelongTo instanceof List)) return null;
                return new NotBelongsToPredicate((List<BasicDBObject>) notBelongTo);

            default:
                return null;
        }
    }
    public void setType(Type type) {
        this.type = type;
    }
}
