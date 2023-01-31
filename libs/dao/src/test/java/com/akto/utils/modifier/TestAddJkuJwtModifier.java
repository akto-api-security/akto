package com.akto.utils.modifier;

import com.akto.dao.context.Context;
import com.akto.util.modifier.AddJkuJWTModifier;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.Test;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.junit.Assert.*;

public class TestAddJkuJwtModifier {

    @Test
    public void testJwtModify() throws Exception {
        AddJkuJWTModifier addJkuJWTModifier =  new AddJkuJWTModifier();
        byte [] decoded = Base64.getDecoder().decode(addJkuJWTModifier.getPublicKey());
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey publicKey = kf.generatePublic(keySpec);

        // this jwt contains both iat and exp
       String modifiedJWT = addJkuJWTModifier.jwtModify("token", "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMiwiZXhwIjoxNTE2MjM5MDkwfQ.qnloZdeBgTtHUtvyhpo37TaxF1CZOLRI0rztKoITjm0NLJBpRqHB9mryP_Zy4FgV8MXDJb0-83pwjqOh8bdIWQKGukQaEL-dwetXCv9N8rWu3yud1ETtgvSNf5_k7X4X02ZbXeOW-qG39E60xtqvPySZ0zlaHbttZfpYus7SrklFoPFZFtDvzAXamFuLjZ1DIrgrW0i7BHl2CmwyEJS2IB3vpYdJzeN0ONlw7WYuVGxfG7BHeyUl7fI51dZHzYEBxvR4o0bZHxRqEWPQDwqAmc2Nbf_LA9p1muJakxT8DgNMxb3cbwusKpes9Ff7seBy1_tFke5HTViB4tO__XOeNw");

       // this will fail if invalid JWT (signature + time)
        Jws<Claims> jws = Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(modifiedJWT);

        assertEquals(AddJkuJWTModifier.JKU_VALUE , jws.getHeader().get("jku"));
        long iat = Long.parseLong(jws.getBody().get("iat").toString());
        long exp = Long.parseLong(jws.getBody().get("exp").toString());
        assertTrue((Context.now() - iat) < 5);
        assertEquals(68, (exp - iat)); // 68 is difference between original JWT's exp and iat

        // this jwt contains iat only
        modifiedJWT = addJkuJWTModifier.jwtModify("token", "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.NHVaYe26MbtOYhSKkoKYdFVomg4i8ZJd8_-RU8VNbftc4TSMb4bXP3l3YlNWACwyXPGffz5aXHc6lty1Y2t4SWRqGteragsVdZufDn5BlnJl9pdR_kdVFUsra2rWKEofkZeIC4yWytE58sMIihvo9H1ScmmVwBcQP6XETqYd0aSHp1gOa9RdUPDvoXQ5oqygTqVtxaDr6wUFKrKItgBMzWIdNZ6y7O9E0DhEPTbE9rfBo6KTFsHAZnMg4k68CDp2woYIaXbmYTWcvbzIuHO7_37GT79XdIwkm95QJ7hYC9RiwrV7mesbY4PAahERJawntho0my942XheVLmGwLMBkQ");

        // this will fail if invalid JWT (signature + time)
        jws = Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(modifiedJWT);

        assertEquals(AddJkuJWTModifier.JKU_VALUE , jws.getHeader().get("jku"));
        iat = Long.parseLong(jws.getBody().get("iat").toString());
        assertTrue((Context.now() - iat) < 5);
        assertNull(jws.getBody().get("exp"));
    }
}
