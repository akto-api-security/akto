package com.akto.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.bson.Document;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dto.AuthToken;
import com.akto.util.AuthTokenIds;

public class TestAuthTokensDao extends MongoBasedTest {

    @Test
    public void deleteAtIsThirtyDaysAfterExpiryAndUnsetForNeverExpiring() {
        assertNull(AuthTokenIds.deleteAtOf(0));
        assertNull(AuthTokenIds.deleteAtOf(-1));
        assertEquals(new Date((1_700_000_000L + 30L * 24 * 3600) * 1000L), AuthTokenIds.deleteAtOf(1_700_000_000));
    }

    @Test
    public void hintIsTheSignatureTailAndIdIsTheSignatureHash() {
        assertEquals("naturetail", AuthTokenIds.hintOf("header.payload.signaturetail"));
        assertEquals("abc", AuthTokenIds.hintOf("abc"));
        String id = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        assertEquals(id, AuthTokenIds.idOf("header.payload.abc"));
        assertEquals(id, AuthTokenIds.idOf("  Bearer other.payload.abc "));
        assertNotEquals(id, AuthTokenIds.idOf("header.payload.abd"));
    }

    @Test
    public void ttlIndexOnDeleteAtExpiresDocumentsAtTheDate() {
        AuthTokensDao.instance.createIndicesIfAbsent();

        Document ttl = null;
        for (Document index : AuthTokensDao.instance.getMCollection().listIndexes()) {
            if (((Document) index.get("key")).containsKey(AuthToken.DELETE_AT)) {
                ttl = index;
            }
        }
        assertTrue("deleteAt TTL index missing", ttl != null);
        assertEquals(0, ((Number) ttl.get("expireAfterSeconds")).intValue());
    }
}
