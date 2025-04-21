package com.akto.test_editor.filter.data_operands_impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.util.Constants;

import static com.akto.runtime.utils.Utils.parseCookie;

public class CookieExpireFilter extends DataOperandsImpl {

    private static List<Boolean> querySet = new ArrayList<>();
    private static Boolean queryVal;

    public static int getMaxAgeFromCookie(Map<String,String> cookieMap){
        if (cookieMap.containsKey("Max-Age") || cookieMap.containsKey("max-age")) {
            int maxAge;
            if (cookieMap.containsKey("Max-Age")) {
                maxAge = Integer.parseInt(cookieMap.get("Max-Age"));
            } else {
                maxAge = Integer.parseInt(cookieMap.get("max-age"));
            }
            return maxAge;
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
            return (int) seconds;
        }
        return -1;
    }
    
    @Override
    public ValidationResult isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        querySet.clear();
        try {

            querySet = (List<Boolean>) dataOperandFilterRequest.getQueryset();
            queryVal = (Boolean) querySet.get(0);
            dataStr = (String) dataOperandFilterRequest.getData();
        } catch(Exception e) {
            return ValidationResult.getInstance().resetValues(false, ValidationResult.GET_QUERYSET_CATCH_ERROR);
        }

        if (dataStr == null || queryVal == null) {
            return ValidationResult.getInstance().resetValues(false, queryVal == null ? TestEditorEnums.DataOperands.COOKIE_EXPIRE_FILTER.name().toLowerCase() + " is not set true": "no data to be matched for validation");
        }

        Map<String,String> cookieMap = parseCookie(Arrays.asList(dataStr));

        boolean result = queryVal;
        boolean res = false;

        int maxAgeOfCookieTs = getMaxAgeFromCookie(cookieMap);
        res = maxAgeOfCookieTs/(Constants.ONE_MONTH_TIMESTAMP) > 1;
        if (result == res) {
            return ValidationResult.getInstance().resetValues(true, result? TestEditorEnums.DataOperands.COOKIE_EXPIRE_FILTER.name().toLowerCase() + ": true passed because cookie:"+ dataStr+" expired":
            TestEditorEnums.DataOperands.COOKIE_EXPIRE_FILTER.name().toLowerCase() + ": false passed because cookie:"+ dataStr+" not expired");
        }
        if (result) {
            return ValidationResult.getInstance().resetValues(false, TestEditorEnums.DataOperands.COOKIE_EXPIRE_FILTER.name().toLowerCase() + ": true failed cookie:"+ dataStr+" not expired");
        }
        return ValidationResult.getInstance().resetValues(false, TestEditorEnums.DataOperands.COOKIE_EXPIRE_FILTER.name().toLowerCase() + ": false failed because cookie:"+ dataStr+" expired");
    }
}
