package com.akto.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.testing.AuthParam;

import okhttp3.Cookie;

public class TokenKVModifier {
    
    public static Boolean tokenKVModifier(OriginalHttpRequest request, String key, String value, AuthParam.Location where) {
        if (where.toString().equals(AuthParam.Location.BODY.toString())) {
            try {
                String resp = JsonStringKVModifier.jsonStringKVModifier(request.getBody(), key, value);
                request.setBody(resp);
            } catch(Exception e) {
                return false;
            }
        }
        else {
            Map<String, List<String>> headers = request.getHeaders();
            List<String> cookieList = headers.getOrDefault("cookie", new ArrayList<>());
            String k = key.toLowerCase().trim();
            if (value == null || value == "null") {
                headers.remove(k);
                CookieTransformer.modifyCookie(cookieList, key, value);
            } else {
                if (headers.containsKey(k)) {
                    headers.put(k, Collections.singletonList(value));
                }
                if (CookieTransformer.isKeyPresentInCookie(cookieList, key)) {
                    CookieTransformer.modifyCookie(cookieList, key, value);
                }
            }
            
        }
        return true;
    }
}
