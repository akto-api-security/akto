package com.akto.test_editor.filter.data_operands_impl;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

        if (cookieMap.containsKey("Max-Age")) {
            int maxAge = Integer.parseInt(cookieMap.get("Max-Age"));
            if (maxAge/(60*60*24) > 30) {
                return false == queryVal;
            }
        } else if (cookieMap.containsKey("Expires")) {
            String expiresTs = cookieMap.get("Expires");
            DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
            ZonedDateTime dateTime = ZonedDateTime.parse(expiresTs, formatter);
            
            ZonedDateTime now = ZonedDateTime.now();
            Duration duration = Duration.between(now, dateTime);
            long seconds = duration.getSeconds();
            if (seconds/(60*60*24) > 30) {
                return false == queryVal;
            }
        } else {
            return true == queryVal;
        }

        return true == queryVal;
    }
}
