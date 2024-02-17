package com.akto.test_editor.filter.data_operands_impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.runtime.policies.AuthPolicy;

public class CookieExpireFilter extends DataOperandsImpl {
    
    @Override
    public Boolean isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        List<Boolean> querySet = new ArrayList<>();
        Boolean queryVal;
        String data;
        try {

            querySet = (List<Boolean>) dataOperandFilterRequest.getQueryset();
            queryVal = (Boolean) querySet.get(0);
            data = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return false;
        }

        if (data == null || queryVal == null) {
            return false;
        }

        Map<String,String> cookieMap = AuthPolicy.parseCookie(Arrays.asList(data));

        boolean result = queryVal;
        boolean res = false;
        if (cookieMap.containsKey("Max-Age") || cookieMap.containsKey("max-age")) {
            int maxAge;
            if (cookieMap.containsKey("Max-Age")) {
                maxAge = Integer.parseInt(cookieMap.get("Max-Age"));
            } else {
                maxAge = Integer.parseInt(cookieMap.get("max-age"));
            }
            if (maxAge/(60*60*24) > 30) {
                res = true;
            }
        } else if (cookieMap.containsKey("Expires") || cookieMap.containsKey("expires")) {
            String expiresTs;
            if (cookieMap.containsKey("Expires")) {
                expiresTs = cookieMap.get("Expires");
            } else {
                expiresTs = cookieMap.get("expires");
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
            LocalDateTime dateTime;
            try {
                dateTime = LocalDateTime.parse(expiresTs, formatter);
            } catch (Exception e) {
                formatter = DateTimeFormatter.ofPattern("EEE, dd-MMM-yyyy HH:mm:ss z", Locale.ENGLISH);
                dateTime = LocalDateTime.parse(expiresTs, formatter);
            }
            LocalDateTime now = LocalDateTime.now();
            Duration duration = Duration.between(now, dateTime);
            long seconds = duration.getSeconds();
            if (seconds/(60*60*24) > 30) {
                res = true;
            }
        }
        return result == res;
    }
}
