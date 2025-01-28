package com.akto.dto.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.testing.AuthParam.Location;
import com.akto.util.CookieTransformer;
import com.akto.util.JSONUtils;
import com.mongodb.BasicDBObject;

public class Utils {
    public static boolean isRequestKeyPresent(String key, OriginalHttpRequest request, Location where){
        if (key == null) return false;
        String k = key.toLowerCase().trim();
        if (where.toString().equals(AuthParam.Location.BODY.toString())) {
            BasicDBObject basicDBObject =  BasicDBObject.parse(request.getBody());
            BasicDBObject data = JSONUtils.flattenWithDots(basicDBObject);
            boolean exists = data.keySet().contains(key);
            if(!exists){
                for(String payloadKey: data.keySet()){
                    if(payloadKey.contains(key)){
                        exists = true;
                        break;
                    }
                }
            }
            return exists;
        } else {
            Map<String, List<String>> headers = request.getHeaders();
            List<String> cookieList = headers.getOrDefault("cookie", new ArrayList<>());
            return headers.containsKey(k) || CookieTransformer.isKeyPresentInCookie(cookieList, k);
        }
    }
}
