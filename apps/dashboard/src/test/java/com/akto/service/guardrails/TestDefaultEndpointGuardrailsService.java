package com.akto.service.guardrails;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.GuardrailPoliciesDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestDefaultEndpointGuardrailsService extends MongoBasedTest {

    private final DefaultEndpointGuardrailsService service = new DefaultEndpointGuardrailsService();

    @Before
    public void reset() {
        clearState();
    }

    // The context-collections cache (120s TTL) is static: left populated, it would leak this test's collections into other test classes.
    @After
    public void cleanUp() {
        clearState();
    }

    private static void clearState() {
        ApiCollectionsDao.instance.getMCollection().drop();
        GuardrailPoliciesDao.instance.getMCollection().drop();
        for (CONTEXT_SOURCE source : CONTEXT_SOURCE.values()) {
            UsersCollectionsList.deleteContextCollectionsForUser(ACCOUNT_ID, source);
        }
        Context.userId.remove();
        Context.contextSource.remove();
    }

    private static ApiCollection collection(int id, String name, List<CollectionTags> tags) {
        ApiCollection c = new ApiCollection(id, name, Context.now(), new HashSet<>(), name, 0, false, false);
        if (tags != null) {
            c.setTagsList(tags);
        }
        return c;
    }

    private static List<CollectionTags> tags(String key, String value) {
        return Collections.singletonList(new CollectionTags(0, key, value, null));
    }

    private void insertEndpointCollection() {
        ApiCollectionsDao.instance.insertOne(collection(1, "endpoint-host",
                tags(Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE)));
    }

    private long policyCount() {
        return GuardrailPoliciesDao.instance.getMCollection().countDocuments(new BasicDBObject());
    }

    // ── definitions ─────────────────────────────────────────────────────────────

    @Test
    public void definitionsAreFourDistinctEndpointPolicies() {
        List<GuardrailPolicies> policies = DefaultEndpointGuardrailPolicies.build(1234);

        assertEquals(4, policies.size());
        assertEquals(4, policies.stream().map(GuardrailPolicies::getName).collect(Collectors.toSet()).size());
        for (GuardrailPolicies p : policies) {
            assertEquals(CONTEXT_SOURCE.ENDPOINT, p.getContextSource());
            assertEquals("default@akto.io", p.getCreatedBy());
            assertEquals(1234, p.getCreatedTimestamp());
            assertTrue(p.isActive());
            assertTrue(p.isApplyToAllServers());
        }
    }

    @Test
    public void definitionsKeepTheirDetectorConfig() {
        List<GuardrailPolicies> policies = DefaultEndpointGuardrailPolicies.build(1);
        GuardrailPolicies pii = byName(policies, "Default-Customer PII");
        GuardrailPolicies code = byName(policies, "Default-Source code detection");
        GuardrailPolicies creds = byName(policies, "Default-Credentials Alert");
        GuardrailPolicies finance = byName(policies, "Default-Financial Advice");

        assertEquals(22, pii.getPiiTypes().size());
        assertTrue(pii.isApplyOnResponse());
        assertTrue(code.getBanCodeDetection().isEnabled());
        assertFalse(code.isApplyOnResponse());
        assertTrue(creds.getSecretsDetection().isEnabled());
        assertEquals(2, creds.getPiiTypes().size());
        assertTrue(finance.getLlmRule().isEnabled());
        assertEquals(1, finance.getDeniedTopics().size());
        assertEquals(12, finance.getDeniedTopics().get(0).getSamplePhrases().size());
        assertTrue(finance.getLlmRule().getUserPrompt().contains("user’s stated intent"));
    }

    private static GuardrailPolicies byName(List<GuardrailPolicies> policies, String name) {
        return policies.stream().filter(p -> name.equals(p.getName())).findFirst().orElseThrow(AssertionError::new);
    }

    // ── eligibility ─────────────────────────────────────────────────────────────

    @Test
    public void accountWithoutAnyCollectionGetsNothing() {
        assertEquals(0, service.seedCurrentAccount());
        assertEquals(0, policyCount());
    }

    @Test
    public void accountWithOnlyNonEndpointCollectionsGetsNothing() {
        ApiCollectionsDao.instance.insertOne(collection(1, "untagged", null));
        ApiCollectionsDao.instance.insertOne(collection(2, "other-source", tags(Constants.AKTO_ENDPOINT_SOURCE_TAG, "AGENTIC")));
        ApiCollectionsDao.instance.insertOne(collection(3, "mcp", tags(Constants.AKTO_MCP_SERVER_TAG, "true")));

        assertFalse(ApiCollectionsDao.instance.hasEndpointCollection());
        assertEquals(0, service.seedCurrentAccount());
        assertEquals(0, policyCount());
    }

    @Test
    public void oneEndpointCollectionAmongOthersIsEnough() {
        ApiCollectionsDao.instance.insertOne(collection(1, "untagged", null));
        ApiCollectionsDao.instance.insertOne(collection(2, "mcp", tags(Constants.AKTO_MCP_SERVER_TAG, "true")));
        ApiCollectionsDao.instance.insertOne(collection(3, "endpoint-host",
                tags(Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE)));

        assertTrue(ApiCollectionsDao.instance.hasEndpointCollection());
    }

    // @Test
    // public void hasEndpointCollectionRestoresThreadLocals() {
    //     insertEndpointCollection();

    //     assertTrue(ApiCollectionsDao.instance.hasEndpointCollection());
    //     assertNull(Context.userId.get());
    //     assertNull(Context.contextSource.get());

    //     Context.userId.set(77);
    //     Context.contextSource.set(CONTEXT_SOURCE.MCP);
    //     assertTrue(ApiCollectionsDao.instance.hasEndpointCollection());
    //     assertEquals(Integer.valueOf(77), Context.userId.get());
    //     assertEquals(CONTEXT_SOURCE.MCP, Context.contextSource.get());
    // }

    // ── seeding ─────────────────────────────────────────────────────────────────

    @Test
    public void seedsFourPoliciesAndRoundTripsThemThroughMongo() {
        insertEndpointCollection();

        assertEquals(4, service.seedCurrentAccount());
        assertEquals(4, policyCount());

        Document raw = GuardrailPoliciesDao.instance.getMCollection().withDocumentClass(Document.class)
                .find(Filters.eq("name", "Default-Financial Advice")).first();
        assertNotNull(raw);
        assertEquals("ENDPOINT", raw.getString("contextSource"));
        assertEquals("default@akto.io", raw.getString("createdBy"));
        assertEquals("alert", raw.getString("behaviour"));
        assertTrue(((Document) raw.get("llmRule")).getString("userPrompt").startsWith("You are evaluating content"));

        GuardrailPolicies pii = GuardrailPoliciesDao.instance.findOne(Filters.eq("name", "Default-Customer PII"));
        assertEquals(22, pii.getPiiTypes().size());
    }

    @Test
    public void secondRunInsertsNothing() {
        insertEndpointCollection();

        assertEquals(4, service.seedCurrentAccount());
        assertEquals(0, service.seedCurrentAccount());
        assertEquals(4, policyCount());
    }

    @Test
    public void existingPolicyIsNeverOverwritten() {
        insertEndpointCollection();
        service.seedCurrentAccount();
        GuardrailPoliciesDao.instance.getMCollection().updateOne(Filters.eq("name", "Default-Customer PII"),
                Updates.combine(Updates.set("name", "Customer renamed"), Updates.set("active", false)));

        assertEquals(0, service.seedCurrentAccount());

        GuardrailPolicies renamed = GuardrailPoliciesDao.instance.findOne(Filters.eq("name", "Customer renamed"));
        assertNotNull(renamed);
        assertFalse(renamed.isActive());
        assertEquals(4, policyCount());
    }

    // ── skip rule: createdBy or name already present ────────────────────────────

    private static GuardrailPolicies customerPolicy(String name, String createdBy, CONTEXT_SOURCE contextSource) {
        GuardrailPolicies p = new GuardrailPolicies();
        p.setName(name);
        p.setCreatedBy(createdBy);
        p.setContextSource(contextSource);
        p.setActive(true);
        return p;
    }

    @Test
    public void deletingOneDefaultPolicyDoesNotBringItBack() {
        insertEndpointCollection();
        service.seedCurrentAccount();
        GuardrailPoliciesDao.instance.getMCollection().deleteOne(Filters.eq("name", "Default-Credentials Alert"));

        assertEquals(0, service.seedCurrentAccount());
        assertEquals(3, policyCount());
    }

    @Test
    public void deletingAllDefaultPoliciesSeedsThemAgain() {
        insertEndpointCollection();
        service.seedCurrentAccount();
        GuardrailPoliciesDao.instance.getMCollection().deleteMany(new BasicDBObject());

        assertEquals(4, service.seedCurrentAccount());
        assertEquals(4, policyCount());
    }

    @Test
    public void anyPolicyCreatedByDefaultUserSkipsTheAccount() {
        insertEndpointCollection();
        GuardrailPoliciesDao.instance.insertOne(customerPolicy("Some other name", "default@akto.io", CONTEXT_SOURCE.ENDPOINT));

        assertEquals(0, service.seedCurrentAccount());
        assertEquals(1, policyCount());
    }

    @Test
    public void aCustomerPolicyWithADefaultNameSkipsTheAccount() {
        insertEndpointCollection();
        GuardrailPoliciesDao.instance.insertOne(customerPolicy("Default-Financial Advice", "someone@customer.com", CONTEXT_SOURCE.ENDPOINT));

        assertEquals(0, service.seedCurrentAccount());
        assertEquals(1, policyCount());
    }

    @Test
    public void unrelatedEndpointPoliciesDoNotSkipTheAccount() {
        insertEndpointCollection();
        GuardrailPoliciesDao.instance.insertOne(customerPolicy("My own policy", "someone@customer.com", CONTEXT_SOURCE.ENDPOINT));

        assertEquals(4, service.seedCurrentAccount());
        assertEquals(5, policyCount());
    }

    @Test
    public void sameNameOrCreatorInAnotherContextDoesNotSkipTheAccount() {
        insertEndpointCollection();
        GuardrailPoliciesDao.instance.insertOne(customerPolicy("Default-Customer PII", "default@akto.io", CONTEXT_SOURCE.AGENTIC));

        assertEquals(4, service.seedCurrentAccount());
        assertEquals(5, policyCount());
    }

    @Test
    public void deleteByCreatedByAndNameOnlyRemovesTheSeededPolicies() {
        List<String> names = DefaultEndpointGuardrailPolicies.build(1).stream()
                .map(GuardrailPolicies::getName).collect(Collectors.toList());
        for (GuardrailPolicies p : DefaultEndpointGuardrailPolicies.build(1)) {
            GuardrailPoliciesDao.instance.insertOne(p);
        }
        GuardrailPoliciesDao.instance.insertOne(customerPolicy("Default-Customer PII", "someone@customer.com", CONTEXT_SOURCE.ENDPOINT));
        GuardrailPoliciesDao.instance.insertOne(customerPolicy("Default-Customer PII", "default@akto.io", CONTEXT_SOURCE.AGENTIC));

        GuardrailPoliciesDao.instance.deleteByCreatedByAndName(CONTEXT_SOURCE.ENDPOINT, "default@akto.io", names);

        assertEquals(2, policyCount());
    }
}
