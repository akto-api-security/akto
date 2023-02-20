package com.akto.utils.modifier;

import com.akto.util.modifier.NoneAlgoJWTModifier;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestNoneAlgoJWTModifier {

    @Test
    public void testJWTModify() throws Exception {
        NoneAlgoJWTModifier noneAlgoJWTModifier = new NoneAlgoJWTModifier("none");
        String jwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpZCI6MiwiaWF0IjoxNTczMzU4Mzk2fQ.RwNNHvOKZk8p6fICIeezuajDalK8ZSOkEGMhZsRPFSk";
        String modifiedJWT = noneAlgoJWTModifier.jwtModify("token", jwt);
        assertEquals("eyJ0eXAiOiJKV1QiLCJhbGciOiJub25lIn0.eyJpZCI6MiwiaWF0IjoxNTczMzU4Mzk2fQ.", modifiedJWT);

        jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        modifiedJWT = noneAlgoJWTModifier.jwtModify("token", jwt);
        assertEquals("eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.", modifiedJWT);

    }
}
