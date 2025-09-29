package com.akto.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.testing.AuthParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenPayloadModifier {
    private static final Logger logger = LoggerFactory.getLogger(TokenPayloadModifier.class);

    public static Boolean tokenPayloadModifier(OriginalHttpRequest request, String key, String value, AuthParam.Location where) {
        if (where.toString().equals(AuthParam.Location.BODY.toString())) {
            try {
                String resp = JsonStringPayloadModifier.jsonStringPayloadModifier(request.getBody(), key, value);
                request.setBody(resp);
            } catch(Exception e) {
                logger.info("error adding auth param to body" + e.getMessage());
                return false;
            }
        }
        else {
            Map<String, List<String>> headers = request.getHeaders();
            List<String> cookieList = headers.getOrDefault("cookie", new ArrayList<>());
            String k = key.toLowerCase().trim();
            if (value == null || value.equals("null")) {
                headers.remove(k);
                CookieTransformer.modifyCookie(cookieList, key, value);
            } else {
                if (headers.containsKey(k)) {
                    headers.put(k, Arrays.asList(value));
                }
                if (CookieTransformer.isKeyPresentInCookie(cookieList, key)) {
                    CookieTransformer.modifyCookie(cookieList, key, value);
                }
            }
            
        }
        return true;
    }
}
