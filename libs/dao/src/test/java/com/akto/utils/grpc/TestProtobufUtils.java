package com.akto.utils.grpc;

import com.akto.util.grpc.ProtoBufUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class TestProtobufUtils {
    @Test
    public void testProtobufDecoder () {
        String str1 = "AAAAAAkKBVdvcmxkEAU="; //encoded string
        Map map = ProtoBufUtils.getInstance().decodeProto(str1);
        Assert.assertTrue("World".equals(map.get("param_1")));
    }
}
