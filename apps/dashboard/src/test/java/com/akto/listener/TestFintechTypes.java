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
        String fileUrl = "https://raw.githubusercontent.com/akto-api-security/akto/develop/pii-types/fintech.json";
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
                    break;
                case "US Medicare Health Insurance Claim Number":
                    assertTrue(cdt.validate("123456789A1", "foo"));
                    assertFalse(cdt.validate("ACDE9458J", "foo"));
                    break;
                case "Indian Unique Health Identification":
                    assertTrue(cdt.validate("12345678912345", "foo"));
                    assertFalse(cdt.validate("ACDE9458J", "foo"));
                    break;
                case "United Kingdom National Insurance Number":
                    assertTrue(cdt.validate("AA123456A", "foo"));
                    assertFalse(cdt.validate("ACDE9458J", "foo"));
                    break;
                case "Finnish Personal Identity Number":
                    assertTrue(cdt.validate("210698-200T", "foo"));
                    assertFalse(cdt.validate("ACDE9458J", "foo"));
                    break;
                case "Canadian Social Insurance Number":
                    assertTrue(cdt.validate("123456789", "foo"));
                    assertFalse(cdt.validate("ACDE9458J", "foo"));
                    break;
                case "German Insurance Identity Number":
                    assertTrue(cdt.validate("12250953M123", "foo"));
                    assertFalse(cdt.validate("ACDE9458J", "foo"));
                    break;
                case "Japanese Social Insurance Number":
                    assertTrue(cdt.validate("012345678912", "foo"));
                    assertFalse(cdt.validate("ACDE9458J", "foo"));
                    break;
                case "IBAN EUROPE":
                    assertTrue(cdt.validate("AB 12 3456 7890 1234 5678", "foo"));
                    assertFalse(cdt.validate("AB 12 3456 7890 1234 5678 912", "foo"));
                    break;
                case "US ADDRESS":
                    assertTrue(cdt.validate("123 MAIN ST, SAN JOSE, CA 11111", "foo"));
                    assertFalse(cdt.validate("PO BOX 123, SAN JOSE, CA 11111", "foo"));
                    break;
                case "FACEBOOK COOKIES":
                    assertTrue(cdt.validate("_fbp", "foo"));
                    assertFalse(cdt.validate("_fdc", "foo"));
                    break;
                case "INTERCOM COOKIES":
                    assertTrue(cdt.validate("intercom-id-app1", "foo"));
                    assertFalse(cdt.validate("interccd-id", "foo"));
                    break;
                case "GOOGLE ADS COOKIES":
                    assertTrue(cdt.validate("_ga", "foo"));
                    assertFalse(cdt.validate("_gg", "foo"));
                    break;
                case "HOTJAR COOKIES":
                    assertTrue(cdt.validate("_hjSessionUser_4455", "foo"));
                    assertFalse(cdt.validate("_hjFirstSeen", "foo"));
                    break;
                case "FULLSTORY COOKIES":
                    assertTrue(cdt.validate("fs_uid", "foo"));
                    assertFalse(cdt.validate("fs_lua", "foo"));
                    break;
                case "MIXPANEL COOKIES":
                    assertTrue(cdt.validate("Mp_*_mixpanel", "foo"));
                    assertFalse(cdt.validate("mixpanel", "foo"));
                    break;
                case "APPSFLYER COOKIES":
                    assertTrue(cdt.validate("af_id", "foo"));
                    assertFalse(cdt.validate("af_cid", "foo"));
                    break;
                default:
                    break;
            }
            
        }
    }

}
