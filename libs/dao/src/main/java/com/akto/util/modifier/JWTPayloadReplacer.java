package com.akto.util.modifier;

public class JWTPayloadReplacer extends JwtModifier {

    String newJwtToken = null;

    public JWTPayloadReplacer(String newJwtToken) {
        super();
        this.newJwtToken = newJwtToken;
    }


    @Override
    public String jwtModify(String key, String value) throws Exception {

        String[] jwtArr = value.split("\\.");
        if (jwtArr.length != 3) return null;

        String[] newJwtArr = this.newJwtToken.split("\\.");
        if (newJwtArr.length != 3) return null;
        
        return jwtArr[0] + "." + newJwtArr[1] + "." + jwtArr[2];
    }

}
