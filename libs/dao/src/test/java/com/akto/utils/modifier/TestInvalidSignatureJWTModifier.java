package com.akto.utils.modifier;

import com.akto.util.modifier.InvalidSignatureJWTModifier;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TestInvalidSignatureJWTModifier {

    @Test
    public void testJwtModify() {
        InvalidSignatureJWTModifier invalidSignatureJWTModifier =  new InvalidSignatureJWTModifier();
        String jwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.NHVaYe26MbtOYhSKkoKYdFVomg4i8ZJd8_-RU8VNbftc4TSMb4bXP3l3YlNWACwyXPGffz5aXHc6lty1Y2t4SWRqGteragsVdZufDn5BlnJl9pdR_kdVFUsra2rWKEofkZeIC4yWytE58sMIihvo9H1ScmmVwBcQP6XETqYd0aSHp1gOa9RdUPDvoXQ5oqygTqVtxaDr6wUFKrKItgBMzWIdNZ6y7O9E0DhEPTbE9rfBo6KTFsHAZnMg4k68CDp2woYIaXbmYTWcvbzIuHO7_37GT79XdIwkm95QJ7hYC9RiwrV7mesbY4PAahERJawntho0my942XheVLmGwLMBkQ";
        String modifiedJWT = invalidSignatureJWTModifier.jwtModify("token", jwt);

        assertEquals( jwt.split("\\.")[0], modifiedJWT.split("\\.")[0]);
        assertEquals( jwt.split("\\.")[1], modifiedJWT.split("\\.")[1]);
        assertNotEquals( jwt.split("\\.")[2].split("")[0], modifiedJWT.split("\\.")[2].split("")[0]);


        jwt = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.NHVaYe26MbtOYhSKkoKYdFVomg4i8ZJd8_-RU8VNbftc4TSMb4bXP3l3YlNWACwyXPGffz5aXHc6lty1Y2t4SWRqGteragsVdZufDn5BlnJl9pdR_kdVFUsra2rWKEofkZeIC4yWytE58sMIihvo9H1ScmmVwBcQP6XETqYd0aSHp1gOa9RdUPDvoXQ5oqygTqVtxaDr6wUFKrKItgBMzWIdNZ6y7O9E0DhEPTbE9rfBo6KTFsHAZnMg4k68CDp2woYIaXbmYTWcvbzIuHO7_37GT79XdIwkm95QJ7hYC9RiwrV7mesbY4PAahERJawntho0my942XheVLmGwLMBkQ";
        modifiedJWT = invalidSignatureJWTModifier.jwtModify("token", jwt);

        assertEquals("Bearer",modifiedJWT.split(" ")[0]);

        assertEquals( jwt.split(" ")[1].split("\\.")[0], modifiedJWT.split(" ")[1].split("\\.")[0]);
        assertEquals( jwt.split(" ")[1].split("\\.")[1], modifiedJWT.split(" ")[1].split("\\.")[1]);
        assertNotEquals( jwt.split(" ")[1].split("\\.")[2].split("")[0], modifiedJWT.split(" ")[1].split("\\.")[2].split("")[0]);
    }
}
