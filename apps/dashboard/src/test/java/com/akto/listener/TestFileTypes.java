package com.akto.listener;

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

public class TestFileTypes extends MongoBasedTest {

    @Test
    public void testTypes() {
        String fileUrl = "https://raw.githubusercontent.com/akto-api-security/akto/master/pii-types/filetypes.json";
        PIISource piiSource = new PIISource(fileUrl, 0, 1638571050, 0, new HashMap<>(), true);
        piiSource.setId("File");
        PIISourceDao.instance.insertOne(piiSource);
        InitializerListener.executePIISourceFetch();
        Context.accountId.set(ACCOUNT_ID);
        for (CustomDataType cdt : CustomDataTypeDao.instance.findAll(new BasicDBObject())) {
            switch (cdt.getName().toUpperCase()) {
                case "IMAGE":
                    assertTrue(cdt.validate("image.jpg", "foo"));
                    assertTrue(cdt.validate("image.jpeg", "foo"));
                    assertTrue(cdt.validate("image.SvG", "foo"));
                    assertTrue(cdt.validate("image.txt.pNg", "foo"));
                    assertFalse(cdt.validate("sample_file", "foo"));
                    break;
                case "DATA FILE":
                    assertTrue(cdt.validate("data.tXt", "foo"));
                    assertTrue(cdt.validate("test.svg.css", "foo"));
                    assertTrue(cdt.validate("image.pdf", "foo"));
                    assertTrue(cdt.validate("test.js", "foo"));
                    assertTrue(cdt.validate("font.WOff", "foo"));
                    assertFalse(cdt.validate("sample.file", "foo"));
                    break;

                default:
                    break;
            }

        }
    }

}
