package com.akto.testing_cli;

import static org.junit.Assert.assertEquals;

import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecRegistry;
import org.junit.Test;

import com.akto.DaoInit;
import com.akto.dto.ApiCollection;
import com.akto.util.Constants;

public class TestCli {
    
    @Test
    public void testPojoConversion() {
        CodecRegistry codecRegistry = DaoInit.createCodecRegistry();
        Document doc = new Document(Constants.ID, 112233);
        doc.put(ApiCollection.NAME, "apiCollection");
        doc.put(ApiCollection.HOST_NAME, "host.com");
        doc.put(ApiCollection.VXLAN_ID, 123);
        Codec<ApiCollection> apiCollectionCodec = codecRegistry.get(ApiCollection.class);
        ApiCollection apiCollection = Main.decode(apiCollectionCodec, doc);
        assertEquals(112233, apiCollection.getId());
        assertEquals("apiCollection", apiCollection.getName());
    }
}