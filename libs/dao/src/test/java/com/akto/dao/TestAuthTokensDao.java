package com.akto.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Date;

import org.bson.Document;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.AuthToken;
import com.akto.dto.monitoring.ModuleInfo;
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

    @Test
    public void moduleInfoHasASparseTokenIdIndexThatTheTokenListQueryUses() {
        ModuleInfoDao.instance.createIndicesIfAbsent();

        Document tokenIdIndex = null;
        for (Document index : ModuleInfoDao.instance.getMCollection().listIndexes()) {
            if (((Document) index.get("key")).containsKey(ModuleInfo.TOKEN_ID)) {
                tokenIdIndex = index;
            }
        }
        assertTrue("tokenId index missing", tokenIdIndex != null);
        assertEquals(Boolean.TRUE, tokenIdIndex.get("sparse"));

        Document explain = MCollection.clients[0].getDatabase(ModuleInfoDao.instance.getDBName()).runCommand(
                new Document("explain", new Document("find", ModuleInfoDao.instance.getCollName())
                        .append("filter", new Document(ModuleInfo.TOKEN_ID, new Document("$in", Arrays.asList("a", "b"))))));
        assertTrue("the token list query must use the index: " + explain.toJson(), explain.toJson().contains("IXSCAN"));
    }
}
