package com.akto.listener;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import com.akto.MongoBasedTest;
import com.akto.dao.CustomDataTypeDao;
import com.akto.dao.context.Context;
import com.akto.dao.pii.PIISourceDao;
import com.akto.dto.CustomDataType;
import com.akto.dto.pii.PIISource;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Updates;

import org.junit.Test;

public class TestListener extends MongoBasedTest {
    
    @Test
    public void test() {
        Context.accountId.set(1_000_000);
        PIISourceDao.instance.getMCollection().drop();
        CustomDataTypeDao.instance.getMCollection().drop();

        String filePath = new File("").getAbsolutePath();
        String fileUrl = filePath.concat("/src/test/resources/pii_source.json");
        PIISource piiSource = new PIISource(fileUrl, 0, 1638571050, 0, new HashMap<>(), true);
        piiSource.setId("A");
        
        PIISourceDao.instance.insertOne(piiSource);
        InitializerListener.executePIISourceFetch();
        List<PIISource> piiSources = PIISourceDao.instance.findAll(new BasicDBObject());
        List<CustomDataType> customDataTypes = CustomDataTypeDao.instance.findAll(new BasicDBObject()); 
        assertTrue(customDataTypes.size() == 4);
        assertTrue(piiSources.get(0).getMapNameToPIIType().size() == 2);


        String fileUrl2 = filePath.concat("/src/test/resources/pii_source_2.json");
        PIISourceDao.instance.updateOne("_id", piiSource.getId(), Updates.set("fileUrl", fileUrl2));

        InitializerListener.executePIISourceFetch();

        piiSources = PIISourceDao.instance.findAll(new BasicDBObject());
        customDataTypes = CustomDataTypeDao.instance.findAll(new BasicDBObject()); 
        assertTrue(customDataTypes.size() == 5);
        assertTrue(piiSources.get(0).getMapNameToPIIType().size() == 3);

    }

}
