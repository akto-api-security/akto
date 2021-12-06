package com.akto.dto.type;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.akto.dao.context.Context;
import com.akto.dto.type.SingleTypeInfo.ParamId;
import com.akto.dto.type.SingleTypeInfo.SubType;

import org.apache.commons.lang3.math.NumberUtils;

public class KeyTypes {

    public static final Map<SubType, Pattern> patternToSubType = new HashMap<>();
    static {
        patternToSubType.put(SubType.EMAIL, Pattern.compile("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"));
        patternToSubType.put(SubType.URL, Pattern.compile("^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)([).!';/?:,][[:blank:|:blank:]])?$"));
        patternToSubType.put(SubType.CREDIT_CARD, Pattern.compile("^((4\\d{3})|(5[1-5]\\d{2})|(6011)|(7\\d{3}))-?\\d{4}-?\\d{4}-?\\d{4}|3[4,7]\\d{13}$"));
        patternToSubType.put(SubType.SSN, Pattern.compile("^\\d{3}-\\d{2}-\\d{4}$"));
        patternToSubType.put(SubType.UUID, Pattern.compile("^[A-Z0-9]{8}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{12}$"));

    }

    Map<SubType, SingleTypeInfo> occurrences;
    boolean isSensitive;

    public KeyTypes() {
    }

    public KeyTypes(Map<SubType,SingleTypeInfo> occurrences, boolean isSensitive) {
        this.occurrences = occurrences;
        this.isSensitive = isSensitive;
    }

    public List<SingleTypeInfo> getAllTypeInfo() {
        List<SingleTypeInfo> ret = new ArrayList<>();
        ret.addAll(occurrences.values());
        return ret;
    }

    public void process(String url, String method, int responseCode, boolean isHeader, String param, Object object, String userId) {

        SubType subType = findSubType(object);

        SingleTypeInfo singleTypeInfo = occurrences.get(subType);
        if (singleTypeInfo == null) {
            Set<Object> examples = new HashSet<>();
            examples.add(object);

            Set<String> userIds = new HashSet<>();
            userIds.add(userId);
            
            ParamId paramId = new ParamId(url, method, responseCode, isHeader, param, subType);
            singleTypeInfo = new SingleTypeInfo(paramId, examples, userIds, 1, Context.now(), 0);

            occurrences.put(subType, singleTypeInfo);
        }

        singleTypeInfo.incr(object);
    }

    private SubType findSubType(Object o) {
        if (o == null) {
            return SubType.NULL;
        } 

        if (NumberUtils.isDigits(o.toString())) {
            o = Long.parseLong(o.toString());
        }

        if (o instanceof Long) {
            Long l = (Long) o;

            if ( l <= Integer.MAX_VALUE && l >= Integer.MIN_VALUE) {
                return SubType.INTEGER_32;
            } else {
                return SubType.INTEGER_64;
            }
        }

        if (o instanceof Integer) {
            return SubType.INTEGER_32;
        }

        if (NumberUtils.isParsable(o.toString())) {
            o = Float.parseFloat(o.toString());
        }

        if (o instanceof Float || o instanceof Double) {
            return SubType.FLOAT;
        }

        if (o instanceof Boolean) {
            Boolean bool = (Boolean) o;
            return bool ? SubType.TRUE : SubType.FALSE;
        }

        if (o instanceof String) {
            String str = o.toString();
            for(SubType subType: patternToSubType.keySet()) {
                Pattern pattern = patternToSubType.get(subType);
                if(pattern.matcher(str).matches()) {
                    return subType;
                }
            }
            return SubType.GENERIC;
        }

        return SubType.OTHER;
    }

    public Map<SubType,SingleTypeInfo> getOccurrences() {
        return this.occurrences;
    }

    public void setOccurrences(Map<SubType,SingleTypeInfo> occurrences) {
        this.occurrences = occurrences;
    }

    public boolean isIsSensitive() {
        return this.isSensitive;
    }

    public boolean getIsSensitive() {
        return this.isSensitive;
    }

    public void setIsSensitive(boolean isSensitive) {
        this.isSensitive = isSensitive;
    }

    public KeyTypes copy() {
        KeyTypes ret = new KeyTypes(new HashMap<>(), false);
        for(SubType subType: occurrences.keySet()) {
            ret.occurrences.put(subType, occurrences.get(subType).copy());
        }

        return ret;
    }

    @Override
    public String toString() {
        return "{" +
            " occurrences='" + getOccurrences() + "'" +
            ", isSensitive='" + isIsSensitive() + "'" +
            "}";
    }
}
