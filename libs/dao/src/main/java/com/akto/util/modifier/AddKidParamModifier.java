package com.akto.util.modifier;

public class AddKidParamModifier extends JwtModifier{

    @Override
    public String jwtModify(String key, String value) throws Exception {
        
        return addKidParamHeader(value);
    }
}
