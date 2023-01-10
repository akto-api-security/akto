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
                System.out.println("error adding auth param to body" + e.getMessage());
                return false;
            }
        }
        else {
            Map<String, List<String>> headers = request.getHeaders();
            String k = key.toLowerCase().trim();
            if (!headers.containsKey(k)) return false;
            headers.put(k, Collections.singletonList(value));
        }
        return true;
    }
}
