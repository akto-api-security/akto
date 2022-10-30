package com.akto.listener;

import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import com.akto.MongoBasedTest;
import com.akto.dao.CustomDataTypeDao;
import com.akto.dao.pii.PIISourceDao;
import com.akto.dto.CustomDataType;
import com.akto.dto.pii.PIISource;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Updates;

import org.junit.Test;

public class TestListener extends MongoBasedTest {
    
    @Test
    public void test() {

        String fileUrl = "/Users/ankushjain/akto_code/mono/apps/dashboard/src/test/resources/pii_source.json";
        PIISource piiSource = new PIISource(fileUrl, 0, 1638571050, 0, new HashMap<>(), true);
        piiSource.setId("A");
        
        PIISourceDao.instance.insertOne(piiSource);
        InitializerListener.executePIISourceFetch();
        assertTrue(CustomDataTypeDao.instance.findAll(new BasicDBObject()).size() == 2);
        assertTrue(PIISourceDao.instance.findOne("_id", "A").getMapNameToPIIType().size() == 2);


        String fileUrl2 = "/Users/ankushjain/akto_code/mono/apps/dashboard/src/test/resources/pii_source_2.json";
        PIISourceDao.instance.updateOne("_id", piiSource.getId(), Updates.set("fileUrl", fileUrl2));

        InitializerListener.executePIISourceFetch();
        assertTrue(CustomDataTypeDao.instance.findAll(new BasicDBObject()).size() == 3);
        assertTrue(PIISourceDao.instance.findOne("_id", "A").getMapNameToPIIType().size() == 2);

    }

}
