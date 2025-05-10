package com.akto.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

// To initialise Token we need a valid refresh token.
// Valid refresh token conditions: 1. Valid JWT 2. Should exist in db correctly mapped to user
public class Token {
    private String refreshToken = null;
    private String accessToken = null;
    private String username = null;

    private String signedUp = "false";

    public static String generateAccessToken(String username, String signedUp) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        Map<String,Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("signedUp", signedUp);

        return JWT.createJWT(
                "/home/avneesh/Desktop/akto/dashboard/private.pem",
                claims,
                "Akto",
                "login",
                Calendar.HOUR,
                2
        );
        
    }
    
    public Token(String refreshToken) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        Jws<Claims> jws = JWT.parseJwt(refreshToken, "/home/avneesh/Desktop/akto/dashboard/public.pem");
        this.username = jws.getBody().get("username").toString();
        this.signedUp = jws.getBody().get("signedUp").toString();
        this.refreshToken = refreshToken;

        this.accessToken = generateAccessToken(this.username, this.signedUp);
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getUsername() {
        return username;
    }

    public String getSignedUp() {
        return signedUp;
    }
}
