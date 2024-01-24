package com.akto.util.modifier;

public class AddJWKModifier extends JwtModifier{

    @Override
    public String jwtModify(String key, String value) throws Exception {
        
        return addJwkHeader(value);
    }
}
