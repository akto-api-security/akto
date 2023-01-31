package com.akto.util.modifier;

import java.util.HashMap;
import java.util.Map;

public class NoneAlgoJWTModifier extends JwtModifier{
    @Override
    public String jwtModify(String key, String value) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("alg", "none");
        String modifiedJWT = manipulateJWTHeader(value, headers);
        String[] jwtArr = modifiedJWT.split("\\.");
        return jwtArr[0] + "." + jwtArr[1] + ".";
    }
}
