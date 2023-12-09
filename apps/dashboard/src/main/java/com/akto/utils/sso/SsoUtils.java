package com.akto.utils.sso;

import java.util.Map;
import java.util.stream.Collectors;

public class SsoUtils {
    
    public static String getQueryString(Map<String,String> paramMap){
        return paramMap.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    } 

}
