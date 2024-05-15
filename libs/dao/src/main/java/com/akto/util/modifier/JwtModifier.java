package com.akto.util.modifier;

import com.akto.dao.context.Context;
import com.akto.dto.type.KeyTypes;
import com.auth0.jwt.JWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

public abstract class JwtModifier extends PayloadModifier {

    static ObjectMapper mapper = new ObjectMapper();
    public abstract String jwtModify(String key, String value) throws Exception;

    @Override
    public Object modify(String key, Object value) {
        if (value == null) return null;

        String[] splitValue = value.toString().split(" ");
        List<String> finalValue = new ArrayList<>();
        boolean flag = false;

        for (String x: splitValue) {
            if (KeyTypes.isJWT(x)) {
                try {
                    String modifiedString = jwtModify(key, x);
                    finalValue.add(modifiedString);
                    flag = true;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            } else {
                finalValue.add(x);
            }
        }

        return flag ? String.join( " ", finalValue) : null;
    }

    public static String manipulateJWTHeader(String jwt, Map<String, String> extraHeaders) throws Exception {
        Map<String, Object> json = manipulateJWTHeaderToMap(jwt, extraHeaders);
        // rebuild string
        String modifiedHeaderStr = mapper.writeValueAsString(json);
        // encode it and remove trailing =
        String encodedModifiedHeader = Base64.getEncoder().encodeToString(modifiedHeaderStr.getBytes(StandardCharsets.UTF_8));
        if (encodedModifiedHeader.endsWith("=")) encodedModifiedHeader = encodedModifiedHeader.substring(0, encodedModifiedHeader.length()-1);
        if (encodedModifiedHeader.endsWith("=")) encodedModifiedHeader = encodedModifiedHeader.substring(0, encodedModifiedHeader.length()-1);

        String[] jwtArr = jwt.split("\\.");

        return encodedModifiedHeader + "." + jwtArr[1]+ "." + jwtArr[2];
    }

    public static Map<String, Object> manipulateJWTHeaderToMap(String jwt, Map<String, String> extraHeaders) throws Exception {
        String[] jwtArr = jwt.split("\\.");
        if (jwtArr.length != 3) throw new Exception("Not jwt");

        String encodedHeader = jwtArr[0];
        byte[] decodedHeaderBytes = Base64.getDecoder().decode(encodedHeader);
        String decodedHeaderStr = new String(decodedHeaderBytes, StandardCharsets.UTF_8);

        // convert string to map
        Map<String, Object> json = new Gson().fromJson(decodedHeaderStr, Map.class);
        // alg -> none
        for (String key: extraHeaders.keySet()) {
            json.put(key, extraHeaders.get(key));
        }

        return json;
    }

    public static Map<String, Object> manipulateJWTHeaderToMapObject(String jwt, Map<String, Object> extraHeaders) throws Exception {
        String[] jwtArr = jwt.split("\\.");
        if (jwtArr.length != 3) throw new Exception("Not jwt");

        String encodedHeader = jwtArr[0];
        byte[] decodedHeaderBytes = Base64.getDecoder().decode(encodedHeader);
        String decodedHeaderStr = new String(decodedHeaderBytes, StandardCharsets.UTF_8);

        Map<String, Object> json = new Gson().fromJson(decodedHeaderStr, Map.class);
        for (String key: extraHeaders.keySet()) {
            json.put(key, extraHeaders.get(key));
        }

        return json;
    }

    public static String manipulateJWT(String jwt, Map<String, String> extraHeaders, Map<String, String> extraBody, String privateKeyString) throws Exception {

        // add header
        Map<String, Object> manipulatedJwtHeader = manipulateJWTHeaderToMap(jwt, extraHeaders);

        // add payload
        Map<String, Object> bodyJson = extractBodyFromJWT(jwt);
        if (extraBody != null) {
            for (String key: extraBody.keySet()) {
                bodyJson.put(key, extraBody.get(key));
            }
        }

        addTimestampsToJwtBody(bodyJson);

        byte [] decoded = Base64.getDecoder().decode(privateKeyString);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        Key privateKey = kf.generatePrivate(keySpec);

        return Jwts.builder()
                .setHeader(manipulatedJwtHeader)
                .setPayload(mapper.writeValueAsString(bodyJson))
                .signWith(privateKey)
                .compact();

    }

    public static String addKidParamHeader(String jwt) throws Exception {

        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("kid", "/proc/1/comm");
        Map<String, Object> manipulatedJwtHeader = manipulateJWTHeaderToMap(jwt, extraHeaders);

        Map<String, Object> bodyJson = extractBodyFromJWT(jwt);

        for (String key: bodyJson.keySet()) {
            Object value = bodyJson.get(key);
            if (value == null) {
                bodyJson.remove(key);
            }
            if (value instanceof Map) {
                Map<String, Object> valMap = (Map<String, Object>) value;
                for (String valKey: valMap.keySet()) {
                    if (valMap.get(valKey) == null) {
                        valMap.remove(valKey);
                    }
                }
            }
        }

        com.auth0.jwt.algorithms.Algorithm algorithm = com.auth0.jwt.algorithms.Algorithm.HMAC256("systemd");

        return JWT.create()
                .withHeader(manipulatedJwtHeader)
                .withPayload(bodyJson)
                .sign(algorithm);

    }

    public static String addJwkHeader(String jwt) throws Exception {
        
        Map<String, Object> extraHeaders = new HashMap<>();
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();

        // Convert to JWK format
        JWK jwk = new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey)keyPair.getPublic())
            .privateKey((RSAPrivateKey)keyPair.getPrivate())
            .keyUse(KeyUse.SIGNATURE)
            .keyID(UUID.randomUUID().toString())
            .build();

        BasicDBObject jwkParams = new BasicDBObject();
        jwkParams.put("e", jwk.getRequiredParams().get("e"));
        jwkParams.put("kty", jwk.getRequiredParams().get("kty"));
        jwkParams.put("n", jwk.getRequiredParams().get("n"));
        jwkParams.put("kid", jwk.getKeyID());
        extraHeaders.put("kid", jwk.getKeyID());
        extraHeaders.put("jwk", jwkParams);

        Map<String, Object> manipulatedJwtHeader = manipulateJWTHeaderToMapObject(jwt, extraHeaders);

        Map<String, Object> bodyJson = extractBodyFromJWT(jwt);

        addTimestampsToJwtBody(bodyJson);

        return Jwts.builder()
                .setHeader(manipulatedJwtHeader)
                .setPayload(mapper.writeValueAsString(bodyJson))
                .signWith(keyPair.getPrivate())
                .compact();
    }

    public static void addTimestampsToJwtBody(Map<String, Object> bodyJson) {
        String issueTimeKey = "iat";
        String expiryTimeKey = "exp";
        try {
            long issueTime = Double.valueOf((double) bodyJson.get(issueTimeKey)).longValue();
            int newIssueTime = Context.now();
            bodyJson.put(issueTimeKey, newIssueTime+"");
            try {
                long expiryTime =  Double.valueOf((double) bodyJson.get(expiryTimeKey)).longValue();
                long diff = expiryTime - issueTime;
                bodyJson.put(expiryTimeKey, (newIssueTime+diff)+"");
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    public static Map<String, Object> extractBodyFromJWT(String jwt) throws Exception {
        String[] jwtArr = jwt.split("\\.");
        if (jwtArr.length != 3) throw new Exception("Not jwt");

        Base64.Decoder decoder = Base64.getUrlDecoder();

        String body = new String(decoder.decode(jwtArr[1]));

        return new Gson().fromJson(body, Map.class);
    }

    public static String createJWT(Map<String, Object> claims,String issuer, String subject, int expiryUnit,
                                   int expiryDuration, Map<String, Object> headers) throws Exception {


        Calendar calendar = Calendar.getInstance();
        java.util.Date issueTime = calendar.getTime();

        calendar.setTime(issueTime);
        calendar.add(expiryUnit, expiryDuration);
        java.util.Date expiryTime = calendar.getTime();

        String privateKeyString = "";
        byte [] decoded = Base64.getDecoder().decode(privateKeyString);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        Key privateKey = kf.generatePrivate(keySpec);

        return Jwts.builder()
                .setHeader(headers)
                .setIssuer(issuer)
                .setSubject(subject)
                .addClaims(claims)
                .setIssuedAt(issueTime)
                .setExpiration(expiryTime)
                .signWith(privateKey)
                .compact();
    }

    public String getPublicKey() {
        String key = "-----BEGIN PUBLIC KEY-----\n" +
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3mbAcFAQKj0OjHof+mhi\n" +
                "Rfhpyk4Rs96kiQK1mxYCrNxR5Xi/TeJj1oFEv3EJBI5rJyFz483e0shUl641fr7f\n" +
                "XMyEp1Lt2EdGvt9TiwDxKgOMOJOZKcBdbesr1E3egHA2CGt13AZ9a9rEOE4jFZd5\n" +
                "jaHhDPFRu4ZioqL18VfLeHP5f955wb5JtGYioawrBfyj7ZuJEJzSpBsBluImnfbi\n" +
                "krGocEa1VqPJTnpjNFL2CU6kyzjjg2Zsq1kPJ6d8YA//2DhDUfctahSKN00gI/fe\n" +
                "3BwyxwUMXJ9L4QYusuLiXBoZaXLkkWmLa09Fq+OuNiuSjb2wdKhMDdZRisKjvX8+\n" +
                "5QIDAQAB\n" +
                "-----END PUBLIC KEY-----";

        return key.replace("-----BEGIN PUBLIC KEY-----","")
        .replace("-----END PUBLIC KEY-----","")
        .replace("\n","");

    }

    public String getPrivateKey() {
        String key = "-----BEGIN PRIVATE KEY-----\n" +
                "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDeZsBwUBAqPQ6M\n" +
                "eh/6aGJF+GnKThGz3qSJArWbFgKs3FHleL9N4mPWgUS/cQkEjmsnIXPjzd7SyFSX\n" +
                "rjV+vt9czISnUu3YR0a+31OLAPEqA4w4k5kpwF1t6yvUTd6AcDYIa3XcBn1r2sQ4\n" +
                "TiMVl3mNoeEM8VG7hmKiovXxV8t4c/l/3nnBvkm0ZiKhrCsF/KPtm4kQnNKkGwGW\n" +
                "4iad9uKSsahwRrVWo8lOemM0UvYJTqTLOOODZmyrWQ8np3xgD//YOENR9y1qFIo3\n" +
                "TSAj997cHDLHBQxcn0vhBi6y4uJcGhlpcuSRaYtrT0Wr4642K5KNvbB0qEwN1lGK\n" +
                "wqO9fz7lAgMBAAECggEADELD5y0yxvFYxPvSmX55tHvOcT2+khj7HyaMFoGvIhJ/\n" +
                "XVQ7z7JkaKX1wUwdAChN1fltJyjpWNt8dmQ/RL6HF9makpLq09qSFuG+/FHP+c36\n" +
                "RAA3GGsne3DUmL62PoRxJiOuerPM5E5KNQRxwLX6GInNG2aOZ/UvqOGtB2IcsIYx\n" +
                "8dRDp6PeYlAlKnVrGLWv7eDz9YyyL15Wk8SxM1/ahz3NMovz4iXxAsCAd71zzx1P\n" +
                "aOOh3NcHUAvMJ5yojUlmNNsUKHwCtSiGYWe07aPtqPWuiOMGtPJ10hCBvZAtU2ZI\n" +
                "cZ/wd0XkaljtXFAnhJq5RR6m0rY/aSO8fgEiZb4lYQKBgQDy8Y7PF5SHbyYoli+5\n" +
                "4ELC0dUAoklQydAuuvGKLROYZBqUxgPE8TwM9525HPRRR5LjL0Kef6dLivZ8xPeZ\n" +
                "Vie+fF7ZCtd1KAoIuKkZXPn7rXoLr+mYk1nVg/5uvVQ6BQBN0fBEtotIPSuGkE9q\n" +
                "LuPOxU48WgSjiWhH3EyO2euwSQKBgQDqWpJy6Iyry+iB5wZtvLvjjF9zNaqbbUej\n" +
                "l1U6TT5qoICDhVXTsU24dsgHrIaRKRpPlsldbR212Kv2sADc4NCOAY86jWZ9k+7n\n" +
                "rEPsUt7CvQc6ZLmPI2Mqy2ZI2Lmaja/gtparh/Ez7eumXLrAicdQKIVH73vCEZAl\n" +
                "MRLtN/9RvQKBgGq3WTf277OeS3DAqC5KKIlTivFAWFw4ik48qCU+L8FdF6AKa1Vz\n" +
                "ciFwE8Rgx6F8gzVwaR8ei+pPHH8qNmkQw1yVXUSR0psP/3hdRUpy4QyA43+GwmHX\n" +
                "ODrmRDl4ySrDT6LfeV91oDEXTatKcNf/yOnnGbrBABCmJzyJtMd7SmChAoGBAKGd\n" +
                "zJlKHpjrinDrbdeH7NtEFx9Qx1NgzaLX3oLSelT2UypgbYwMHlk0MUZ5iGPbQLXa\n" +
                "ewvfEDo0LoN1ZWLt92W3VZs/oIrB1mQWvNDhZZZO3gk7JWy9Lsp4cxWRwI4BYGVM\n" +
                "BiRNH958GaMlF/VoDvgMub2ePm7bxdigOzk1APLRAoGBAOBYqcqHHJ3P/TMCkSC1\n" +
                "k+YI7B2iNFdN8sWQXoQM1KY6UDnU2oI/Cjv4XkeKWQ78mVX0abXo/bXUwaF/ORpR\n" +
                "sfUo3tgKMVwS14l/ZQ3TDyM6fHwSwLUOJ+OwUn0licfRrOywqPKzah9r3JjBbC5s\n" +
                "K3Qks2pR++7M8QojX+IJfa+a\n" +
                "-----END PRIVATE KEY-----";


        return key.replace("-----BEGIN PRIVATE KEY-----","")
                .replace("-----END PRIVATE KEY-----","")
                .replace("\n","");
    }

}
