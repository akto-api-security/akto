package com.akto.util.modifier;

import java.util.HashMap;
import java.util.Map;

public class AddJkuJWTModifier extends JwtModifier{

    public static final String JKU_VALUE = "https://raw.githubusercontent.com/akto-api-security/pii-types/master/public_key.pem";
    public static final String JKU_HEADER = "jku";

    @Override
    public String jwtModify(String key, String value) throws Exception {
        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put(JKU_HEADER, JKU_VALUE);
        return manipulateJWT(value, extraHeaders, new HashMap<>(),getPrivateKey());
    }
}
