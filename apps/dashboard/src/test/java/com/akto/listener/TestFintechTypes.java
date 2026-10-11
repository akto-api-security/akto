package com.akto.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Paths;
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
        Context.accountId.set(ACCOUNT_ID);
        PIISourceDao.instance.getMCollection().drop();
        CustomDataTypeDao.instance.getMCollection().drop();

        String fileUrl = Paths.get("..", "..", "pii-types", "fintech.json").toAbsolutePath().normalize().toString();
        PIISource piiSource = new PIISource(fileUrl, 0, 1638571050, 0, new HashMap<>(), true);
        piiSource.setId("Fin");
        PIISourceDao.instance.insertOne(piiSource);
        InitializerListener.executePIISourceFetch();
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
                case "SQL DATABASE URL":
                    assertTrue(cdt.validate("jdbc:mysql://db.example.com:3306/customers", "foo"));
                    assertTrue(cdt.validate("postgresql://readonly:secret@db.internal:5432/ledger", "foo"));
                    assertFalse(cdt.validate("https://db.example.com:3306/customers", "foo"));
                    break;
                case "MONGO DATABASE URL":
                    assertTrue(cdt.validate("mongodb://admin:secret@mongo.example.com:27017/app", "foo"));
                    assertTrue(cdt.validate("mongodb+srv://cluster0.example.mongodb.net/sample", "foo"));
                    assertFalse(cdt.validate("mongo://cluster0.example.mongodb.net/sample", "foo"));
                    break;
                case "AMAZON S3 URL":
                    assertTrue(cdt.validate("s3://akto-sensitive-bucket/backups/config.json", "foo"));
                    assertFalse(cdt.validate("https://akto-sensitive-bucket.s3.amazonaws.com/backups/config.json", "foo"));
                    break;
                case "RDS DATABASE URL":
                    assertTrue(cdt.validate("jdbc:postgresql://akto-db.abc123.us-east-1.rds.amazonaws.com:5432/app", "foo"));
                    assertFalse(cdt.validate("jdbc:postgresql://akto-db.abc123.us-east-1.amazonaws.com:5432/app", "foo"));
                    break;
                case "PROMETHEUS DATABASE URL":
                    assertTrue(cdt.validate("prometheus://metrics.internal:9090/api/v1/query", "foo"));
                    assertFalse(cdt.validate("http://metrics.internal:9090/api/v1/query", "foo"));
                    break;
                case "REDIS DATABASE URL":
                    assertTrue(cdt.validate("redis://cache.internal:6379", "foo"));
                    assertTrue(cdt.validate("rediss://cache.internal:6380", "foo"));
                    assertFalse(cdt.validate("redis://cache.internal", "foo"));
                    break;
                default:
                    break;
            }
            
        }
    }

}
