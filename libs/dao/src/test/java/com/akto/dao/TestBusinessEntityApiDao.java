package com.akto.dao;

import com.akto.dto.BusinessEntityApi;
import com.akto.utils.MongoBasedTest;
import org.bson.Document;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class TestBusinessEntityApiDao extends MongoBasedTest {

    // Same shape the business-flow agent writes, including rules / ai / decision that the DTO does not map
    private static Document operationDoc(String id, String scopeKey, String entityId, String operation, String method, String url) {
        Document api = new Document("apiCollectionId", -1739775948).append("method", method).append("url", url)
                .append("hostName", "account.ev-chargepoint.com");
        return new Document("_id", id)
                .append("scopeKey", scopeKey)
                .append("entityId", entityId)
                .append("operation", operation)
                .append("api", api)
                .append("decision", new Document("source", "ai").append("confidence", "high"))
                .append("rules", new Document("api", api).append("entityId", entityId).append("reasons", Arrays.asList("rest URL")))
                .append("ai", new Document("verdict", "confirm").append("entity", entityId).append("reason", "Deletes card"));
    }

    @Test
    public void findOperationsReturnsOnlyBusinessOperationsOfRequestedEntities() {
        BusinessEntityApiDao.instance.getMCollection().drop();
        MCollection.getMCollection(BusinessEntityApiDao.instance.getDBName(), BusinessEntityApiDao.instance.getCollName(), Document.class)
                .insertMany(Arrays.asList(
                        operationDoc("1", "chargepoint", "card", "delete", "DELETE", "/account/v1/driver/cards/INTEGER"),
                        operationDoc("2", "chargepoint", "card", "list", "GET", "/account/v1/driver/cards"),
                        operationDoc("3", "chargepoint", "card", "plumbing", "POST", "/cards/getReportHeaders/x"),
                        operationDoc("4", "chargepoint", "station", "read", "GET", "/api/v1/stations/STRING"),
                        operationDoc("5", "other", "card", "create", "POST", "/account/v1/driver/cards")));

        List<BusinessEntityApi> operations = BusinessEntityApiDao.instance.findOperations("chargepoint", Arrays.asList("card"));

        Set<String> ids = operations.stream().map(BusinessEntityApi::getId).collect(Collectors.toSet());
        assertEquals(2, operations.size());
        assertEquals(new HashSet<>(Arrays.asList("1", "2")), ids);
        BusinessEntityApi delete = operations.stream().filter(o -> o.getId().equals("1")).findFirst().get();
        assertEquals("delete", delete.getOperation());
        assertEquals("/account/v1/driver/cards/INTEGER", delete.getApi().getUrl());
        assertEquals(-1739775948, delete.getApi().getApiCollectionId());
    }
}
