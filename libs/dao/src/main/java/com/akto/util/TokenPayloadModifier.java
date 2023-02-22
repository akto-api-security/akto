package com.akto.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.testing.AuthParam;

public class TokenPayloadModifier {
    
    public static Boolean tokenPayloadModifier(OriginalHttpRequest request, String key, String value, AuthParam.Location where) {
        if (where.toString().equals(AuthParam.Location.BODY.toString())) {
            try {
                String resp = JsonStringPayloadModifier.jsonStringPayloadModifier(request.getBody(), key, value);
                request.setBody(resp);
            } catch(Exception e) {
                return false;
            }
        }
        else {
            Map<String, List<String>> headers = request.getHeaders();
            String k = key.toLowerCase().trim();
            if (value == null || value == "null") {
                headers.remove(k);
            } else {
                headers.put(k, Collections.singletonList(value));
            }
        }
        return true;
    }
}
