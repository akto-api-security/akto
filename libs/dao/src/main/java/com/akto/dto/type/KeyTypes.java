package com.akto.dto.type;

import java.util.*;
import java.util.regex.Pattern;

import com.akto.dao.context.Context;
import com.akto.dto.type.SingleTypeInfo.ParamId;
import com.akto.dto.type.SingleTypeInfo.SubType;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.validator.routines.CreditCardValidator;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.json.JSONObject;

public class KeyTypes {

    public static CreditCardValidator creditCardValidator = new CreditCardValidator();
    public static InetAddressValidator ipAddressValidator = InetAddressValidator.getInstance();
    public static final Map<SubType, Pattern> patternToSubType = new HashMap<>();
    static {
        patternToSubType.put(SubType.EMAIL, Pattern.compile("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"));
        patternToSubType.put(SubType.URL, Pattern.compile("^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))([-%()_.!~*';/?:@&=+$,A-Za-z0-9])+)$"));
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

    public void process(String url, String method, int responseCode, boolean isHeader, String param, Object object, String userId, int apiCollectionId) {

        SubType subType = findSubType(object);

        SingleTypeInfo singleTypeInfo = occurrences.get(subType);
        if (singleTypeInfo == null) {
            Set<Object> examples = new HashSet<>();
            examples.add(object);

            Set<String> userIds = new HashSet<>();
            userIds.add(userId);
            
            ParamId paramId = new ParamId(url, method, responseCode, isHeader, param, subType, apiCollectionId);
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
            if (o.toString().length() < 19) {
                o = Long.parseLong(o.toString());
            }
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
            if (isJWT(str)) {
                return SubType.JWT;
            }

            if (isPhoneNumber(str)) {
                return SubType.PHONE_NUMBER;
            }

            if (isCreditCard(str)) {
                return SubType.CREDIT_CARD;
            }
            if (isIP(str)) {
                return SubType.IP_ADDRESS;
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

    public static boolean isPhoneNumber(String mobileNumber) {
        boolean lengthCondition = mobileNumber.length() < 8 || mobileNumber.length() > 16;
        boolean alphabetsCondition = mobileNumber.toLowerCase() != mobileNumber.toUpperCase(); // contains alphabets

        if (lengthCondition || alphabetsCondition) {
            return false;
        }

        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

        // isPossibleNumber computes faster than parse but less accuracy
        boolean check = phoneNumberUtil.isPossibleNumber(mobileNumber,
                    Phonenumber.PhoneNumber.CountryCodeSource.UNSPECIFIED.name());
        if (!check) {
            return false;
        }

        try {
            Phonenumber.PhoneNumber phone = phoneNumberUtil.parse(mobileNumber,
                    Phonenumber.PhoneNumber.CountryCodeSource.UNSPECIFIED.name());
            return phoneNumberUtil.isValidNumber(phone);
        } catch (Exception e) {
            // eat it
            return false;
        }

    }

    public static boolean isJWT(String jwt) {
        try {
            String[] jwtList = jwt.split("\\.");
            if (jwtList.length != 3) // The JWT is composed of three parts
                return false;
            String jsonFirstPart = new String(Base64.getDecoder().decode(jwtList[0]));
            JSONObject firstPart = new JSONObject(jsonFirstPart); // The first part of the JWT is a JSON
            if (!firstPart.has("alg")) // The first part has the attribute "alg"
                return false;
            String jsonSecondPart = new String(Base64.getDecoder().decode(jwtList[1]));
            JSONObject secondPart = new JSONObject(jsonSecondPart); // The first part of the JWT is a JSON
        }catch (Exception err){
            return false;
        }
        return true;
    }

    public static boolean isCreditCard(String s) {
        if (s.length() < 12 || s.length() > 18) return false;
        if (s.toLowerCase() != s.toUpperCase()) return false; // only numbers
        return creditCardValidator.isValid(s);
    }

    public static boolean isIP(String s) {
        // for edge cases look at test cases of this function
        boolean canBeIpv4 = (s.length() > 6) || (s.length() <= 15) && (s.split(".").length == 4);
        boolean canBeIpv6 = (s.length() < 45 && s.split(":").length > 6);
        if (!(canBeIpv4 || canBeIpv6)) return false;
        return ipAddressValidator.isValid(s);
    }

}
