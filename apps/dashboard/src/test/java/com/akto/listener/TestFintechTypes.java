package com.akto.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import com.akto.MongoBasedTest;
import com.akto.dao.CustomDataTypeDao;
import com.akto.dao.context.Context;
import com.akto.dao.pii.PIISourceDao;
import com.akto.dto.CustomDataType;
import com.akto.dto.pii.PIISource;
import com.mongodb.BasicDBObject;

import org.junit.Test;

public class TestFintechTypes extends MongoBasedTest {


    @Test
    public void testTypes() {
        String fileUrl = "https://raw.githubusercontent.com/akto-api-security/akto/master/pii-types/fintech.json";
        PIISource piiSource = new PIISource(fileUrl, 0, 1638571050, 0, new HashMap<>(), true);
        piiSource.setId("Fin");
        PIISourceDao.instance.insertOne(piiSource);
        InitializerListener.executePIISourceFetch();
        Context.accountId.set(ACCOUNT_ID);
        for(CustomDataType cdt: CustomDataTypeDao.instance.findAll(new BasicDBObject())) {
            switch (cdt.getName().toUpperCase()) {
                case "PAN CARD":
                    assertTrue(cdt.validate("ABCDE9458J", "foo"));
                    assertFalse(cdt.validate("ACDE9458J", "foo"));
                case "Google Ad Cookie":
                    assertTrue(cdt.validate("__gads", "foo"));
                    assertTrue(cdt.validate("_gcl_gb", "foo"));
                    assertTrue(cdt.validate("__gpi", "foo"));
                    break;
                
                default:
                    break;
            }
            
        }
    }

}
