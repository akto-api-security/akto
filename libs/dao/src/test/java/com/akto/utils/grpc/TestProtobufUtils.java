package com.akto.utils.grpc;

import com.akto.util.grpc.ProtoBufUtils;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.util.Map;

public class TestProtobufUtils {
    @Test
    public void testProtobufDecoder () {
        String str1 = "AAAAAAkKBVdvcmxkEAU="; //encoded string
        Map map = ProtoBufUtils.getInstance().decodeProto(str1);
        Assert.assertTrue("World".equals(map.get("param_1")));
    }

    @Test
    public void testIsBase64Encoded(){

        String base64EncodedString = "SGVsbG8sIFdvcmxkIQ==";
        String notBase64EncodedString = "helloworld";

        assertEquals(ProtoBufUtils.isBase64Encoded(notBase64EncodedString), false);
        assertEquals(ProtoBufUtils.isBase64Encoded(base64EncodedString), true);

    }
}
