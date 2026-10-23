package com.akto.dao;

import com.akto.dto.BusinessEntity;
import com.akto.utils.MongoBasedTest;
import org.bson.Document;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestBusinessEntityDao extends MongoBasedTest {

    // Same shape the business-flow agent writes, including fields the DTO does not map (tier, score, valueRange, ...)
    private static Document agentDocument(String slug, String tier, int apiCount) {
        return new Document("_id", "chargepoint:" + slug)
                .append("scopeKey", "chargepoint")
                .append("slug", slug)
                .append("name", slug.substring(0, 1).toUpperCase() + slug.substring(1))
                .append("tier", tier)
                .append("status", "candidate")
                .append("stats", new Document("apiCount", apiCount).append("collectionCount", 6).append("hostCount", 20)
                        .append("requestApis", 16).append("responseApis", 114).append("pathApis", 31))
                .append("evidence", new Document("score", 6).append("domainTerm", true)
                        .append("statefulAttributes", Arrays.asList("name", "status")))
                .append("aliases", Collections.singletonList(new Document("name", "stationId").append("apiCount", 32)))
                .append("roles", Collections.singletonList(new Document("name", "creator").append("apiCount", 3)))
                .append("hosts", Collections.singletonList(new Document("name", "cpnos.ev-chargepoint.com").append("apiCount", 40)))
                .append("parentContexts", Collections.emptyList())
                .append("attributes", Arrays.asList("name", "status", "address"))
                .append("valueRange", new Document("min", 9068261L).append("max", 9086571L))
                .append("sampleApis", Collections.singletonList(new Document("apiCollectionId", -862047973)
                        .append("hostName", "driver-station-search.ev-chargepoint.com").append("method", "POST")
                        .append("url", "/fixed-match/search").append("location", "response").append("param", "stations#$#id")))
                .append("createdAt", new Date())
                .append("updatedAt", new Date());
    }

    @Test
    public void findConfirmedReturnsOnlyConfirmedOfScopeSortedByApiCount() {
        BusinessEntityDao.instance.getMCollection().drop();
        MCollection.getMCollection(BusinessEntityDao.instance.getDBName(), BusinessEntityDao.instance.getCollName(), Document.class)
                .insertMany(Arrays.asList(
                        agentDocument("station", "confirmed", 141),
                        agentDocument("organization", "confirmed", 448),
                        agentDocument("snooze", "review", 5),
                        agentDocument("driver", "confirmed", 62).append("scopeKey", "other")));

        List<BusinessEntity> entities = BusinessEntityDao.instance.findConfirmed("chargepoint");

        assertEquals(2, entities.size());
        assertEquals("chargepoint:organization", entities.get(0).getId());
        BusinessEntity station = entities.get(1);
        assertEquals("Station", station.getName());
        assertEquals(141, station.getStats().getApiCount());
        assertEquals("stationId", station.getAliases().get(0).getName());
        assertEquals("cpnos.ev-chargepoint.com", station.getHosts().get(0).getName());
        assertEquals(Arrays.asList("name", "status"), station.getEvidence().getStatefulAttributes());
        assertEquals("stations#$#id", station.getSampleApis().get(0).getParam());
        assertEquals(-862047973, station.getSampleApis().get(0).getApiCollectionId());
        assertNotNull(station.getCreatedAt());
    }
}
