package com.akto.util.modifier;

import java.util.HashMap;
import java.util.Map;

public class NoneAlgoJWTModifier extends JwtModifier{

    String noneString = null;

    public NoneAlgoJWTModifier(String noneString) {
        super();
        this.noneString = noneString;
    }

    @Override
    public String jwtModify(String key, String value) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("alg", noneString);
        String modifiedJWT = manipulateJWTHeader(value, headers);
        String[] jwtArr = modifiedJWT.split("\\.");
        return jwtArr[0] + "." + jwtArr[1] + ".";
    }
}
