package com.akto.util.modifier;

public class InvalidSignatureJWTModifier extends JwtModifier {
    @Override
    public String jwtModify(String key, String value) {
        String[] jwtArr = value.split("\\.");
        if (jwtArr.length != 3) return null;

        String signature = jwtArr[2];
        String firstChar = signature.split("")[0];
        String finalSignatureFirstChar = firstChar.equals("a") ? "b" : "a" ;

        return jwtArr[0] + "." + jwtArr[1] + "." + finalSignatureFirstChar+signature.substring(1) ;
    }
}
