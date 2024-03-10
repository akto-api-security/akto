package com.akto.util.runtime;

import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuntimeUtil {

    // From api-runtime/com.akto.runtime.URLAggregator.java
    public static URLStatic getBaseURL(String url, String method) {

        if (url == null) {
            return null;
        }

        return new URLStatic(url.split("\\?")[0], URLMethods.Method.fromString(method));
    }

    // From api-runtime/com.akto.runtime.policies.AuthPolicy.java
    public static Map<String,String> parseCookie(List<String> cookieList){
        Map<String,String> cookieMap = new HashMap<>();
        if(cookieList==null)return cookieMap;
        for (String cookieValues : cookieList) {
            String[] cookies = cookieValues.split(";");
            for (String cookie : cookies) {
                cookie=cookie.trim();
                String[] cookieFields = cookie.split("=");
                boolean twoCookieFields = cookieFields.length == 2;
                if (twoCookieFields) {
                    if(!cookieMap.containsKey(cookieFields[0])){
                        cookieMap.put(cookieFields[0], cookieFields[1]);
                    }
                }
            }
        }
        return cookieMap;
    }

    // From api-runtime/com.akto.runtime.APICatalogSync.java
    public static String trim(String url) {
        // if (mergeAsyncOutside) {
        //     if ( !(url.startsWith("/") ) && !( url.startsWith("http") || url.startsWith("ftp")) ){
        //         url = "/" + url;
        //     }
        // } else {
        if (url.startsWith("/")) url = url.substring(1, url.length());
        // }

        if (url.endsWith("/")) url = url.substring(0, url.length()-1);
        return url;
    }
    public static String[] trimAndSplit(String url) {
        return trim(url).split("/");
    }
    public static URLTemplate createUrlTemplate(String url, URLMethods.Method method) {
        String[] tokens = trimAndSplit(url);
        SingleTypeInfo.SuperType[] types = new SingleTypeInfo.SuperType[tokens.length];
        for(int i = 0; i < tokens.length; i ++ ) {
            String token = tokens[i];

            if (token.equals(SingleTypeInfo.SuperType.STRING.name())) {
                tokens[i] = null;
                types[i] = SingleTypeInfo.SuperType.STRING;
            } else if (token.equals(SingleTypeInfo.SuperType.INTEGER.name())) {
                tokens[i] = null;
                types[i] = SingleTypeInfo.SuperType.INTEGER;
            } else if (token.equals(SingleTypeInfo.SuperType.OBJECT_ID.name())) {
                tokens[i] = null;
                types[i] = SingleTypeInfo.SuperType.OBJECT_ID;
            } else if (token.equals(SingleTypeInfo.SuperType.FLOAT.name())) {
                tokens[i] = null;
                types[i] = SingleTypeInfo.SuperType.FLOAT;
            } else {
                types[i] = null;
            }

        }

        URLTemplate urlTemplate = new URLTemplate(tokens, types, method);

        return urlTemplate;
    }


}
