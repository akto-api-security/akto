package com.akto.util.modifier;

import java.util.HashMap;
import java.util.Map;


public class AddJWKModifier extends JwtModifier{

    @Override
    public String jwtModify(String key, String value) throws Exception {
        
        return addJwkHeader(value);
    }
}
