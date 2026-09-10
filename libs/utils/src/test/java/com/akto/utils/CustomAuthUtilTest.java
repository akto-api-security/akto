package com.akto.utils;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dto.ApiInfo;
import com.mongodb.client.model.WriteModel;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.akto.utils.CustomAuthTestDataMother.*;
import static org.junit.Assert.*;

public class CustomAuthUtilTest extends MongoBasedTest {

    @After
    public void tearDown() {
        // Explicitly stop MongoDB after each test to prevent port conflicts
        if (mongod != null) {
            mongod.stop();
        }
        if (mongodExe != null) {
            mongodExe.stop();
        }
    }

    /**
     * Test Group 1: Single Auth Type Detection
     * Tests detection of single auth types from scratch (empty initial state)
     */
    @Test
    public void testCalcAuthWithSingleAuthTypes() {
        // Clear collections
        ApiInfoDao.instance.getMCollection().drop();
        SampleDataDao.instance.getMCollection().drop();

        // Setup: Create 5 APIs with empty auth types and different sample data
        List<ApiInfo> apiInfoList = new ArrayList<>();

        // API 1: Bearer JWT
        ApiInfo api1 = createApiInfoWithNoAuth();
        api1.getId().setUrl("/api/test/bearer");
        apiInfoList.add(api1);
        SampleDataDao.instance.insertOne(createSampleDataWithBearerToken(
            api1.getId().getApiCollectionId(),
            api1.getId().getUrl(),
            api1.getId().getMethod()
        ));

        // API 2: API Key
        ApiInfo api2 = createApiInfoWithNoAuth();
        api2.getId().setUrl("/api/test/apikey");
        apiInfoList.add(api2);
        SampleDataDao.instance.insertOne(createSampleDataWithApiKey(
            api2.getId().getApiCollectionId(),
            api2.getId().getUrl(),
            api2.getId().getMethod()
        ));

        // API 3: Basic Auth
        ApiInfo api3 = createApiInfoWithNoAuth();
        api3.getId().setUrl("/api/test/basic");
        apiInfoList.add(api3);
        SampleDataDao.instance.insertOne(createSampleDataWithBasicAuth(
            api3.getId().getApiCollectionId(),
            api3.getId().getUrl(),
            api3.getId().getMethod()
        ));

        // API 4: MTLS
        ApiInfo api4 = createApiInfoWithNoAuth();
        api4.getId().setUrl("/api/test/mtls");
        apiInfoList.add(api4);
        SampleDataDao.instance.insertOne(createSampleDataWithMtls(
            api4.getId().getApiCollectionId(),
            api4.getId().getUrl(),
            api4.getId().getMethod()
        ));

        // API 5: Session Token
        ApiInfo api5 = createApiInfoWithNoAuth();
        api5.getId().setUrl("/api/test/session");
        apiInfoList.add(api5);
        SampleDataDao.instance.insertOne(createSampleDataWithSessionToken(
            api5.getId().getApiCollectionId(),
            api5.getId().getUrl(),
            api5.getId().getMethod()
        ));

        ApiInfoDao.instance.insertMany(apiInfoList);

        // Execute: Call calcAuth
        List<WriteModel<ApiInfo>> updates = CustomAuthUtil.calcAuth(apiInfoList, null, true);

        // Assert in-memory state: Should have 5 updates (one per API)
        assertEquals("Should have 5 WriteModel updates", 5, updates.size());

        // Verify each API has auth types detected
        for (ApiInfo apiInfo : apiInfoList) {
            assertNotNull("Auth types should not be null", apiInfo.getAllAuthTypesFound());
            assertFalse("Auth types should not be empty", apiInfo.getAllAuthTypesFound().isEmpty());
        }

        // Execute bulk write to persist changes
        if (!updates.isEmpty()) {
            ApiInfoDao.instance.getMCollection().bulkWrite(updates);
        }

        // Assert DB state: Verify persisted auth types
        for (ApiInfo apiInfo : apiInfoList) {
            ApiInfo dbApiInfo = ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(apiInfo.getId()));
            assertNotNull("DB ApiInfo should exist", dbApiInfo);
            assertNotNull("DB auth types should not be null", dbApiInfo.getAllAuthTypesFound());
            assertEquals("In-memory and DB auth types should match",
                apiInfo.getAllAuthTypesFound(),
                dbApiInfo.getAllAuthTypesFound());
        }
    }

    /**
     * Test Group 2: Multiple Auth Types in Single Request
     * Tests detection of multiple auth types present in the same sample
     */
    @Test
    public void testCalcAuthWithMultipleAuthTypes() {
        // Clear collections
        ApiInfoDao.instance.getMCollection().drop();
        SampleDataDao.instance.getMCollection().drop();

        // Setup: Create 2 APIs with multiple auth types in samples
        List<ApiInfo> apiInfoList = new ArrayList<>();

        // API 1: Authorization + MTLS
        ApiInfo api1 = createApiInfoWithNoAuth();
        api1.getId().setUrl("/api/test/auth-mtls");
        apiInfoList.add(api1);
        SampleDataDao.instance.insertOne(createSampleDataWithAuthorizationAndMtls(
            api1.getId().getApiCollectionId(),
            api1.getId().getUrl(),
            api1.getId().getMethod()
        ));

        // API 2: Basic + API Key
        ApiInfo api2 = createApiInfoWithNoAuth();
        api2.getId().setUrl("/api/test/basic-apikey");
        apiInfoList.add(api2);
        SampleDataDao.instance.insertOne(createSampleDataWithBasicAndApiKey(
            api2.getId().getApiCollectionId(),
            api2.getId().getUrl(),
            api2.getId().getMethod()
        ));

        ApiInfoDao.instance.insertMany(apiInfoList);

        // Execute: Call calcAuth
        List<WriteModel<ApiInfo>> updates = CustomAuthUtil.calcAuth(apiInfoList, null, true);

        // Assert in-memory state: Should have 2 updates
        assertEquals("Should have 2 WriteModel updates", 2, updates.size());

        // Verify multiple auth types detected in each API
        for (ApiInfo apiInfo : apiInfoList) {
            Set<Set<ApiInfo.AuthType>> authTypes = apiInfo.getAllAuthTypesFound();
            assertNotNull("Auth types should not be null", authTypes);

            // Each API should have at least one set of auth types
            assertFalse("Auth types should not be empty", authTypes.isEmpty());

            // Verify that at least one set has multiple auth types (if they're grouped together)
            // or multiple individual auth types are detected
            int totalAuthTypeCount = authTypes.stream()
                .mapToInt(Set::size)
                .sum();
            assertTrue("Should detect multiple auth types", totalAuthTypeCount >= 2);
        }

        // Execute bulk write
        if (!updates.isEmpty()) {
            ApiInfoDao.instance.getMCollection().bulkWrite(updates);
        }

        // Assert DB state
        for (ApiInfo apiInfo : apiInfoList) {
            ApiInfo dbApiInfo = ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(apiInfo.getId()));
            assertNotNull("DB ApiInfo should exist", dbApiInfo);
            assertEquals("In-memory and DB auth types should match",
                apiInfo.getAllAuthTypesFound(),
                dbApiInfo.getAllAuthTypesFound());
        }
    }

    /**
     * Test Group 3: Existing Auth Types Preserved/Updated
     * Tests that existing auth types are maintained or enhanced
     */
    @Test
    public void testCalcAuthWithExistingAuthTypes() {
        // Clear collections
        ApiInfoDao.instance.getMCollection().drop();
        SampleDataDao.instance.getMCollection().drop();

        // Setup: Create APIs with existing auth types
        List<ApiInfo> apiInfoList = new ArrayList<>();

        // API 1: Already has JWT+AUTHORIZATION_HEADER, should maintain
        ApiInfo api1 = createApiInfoWithSingleAuthSet();
        apiInfoList.add(api1);
        SampleDataDao.instance.insertOne(createSampleDataWithJwtPrefix());

        // API 2: Already has multiple auth sets, should maintain/update
        ApiInfo api2 = createApiInfoWithMultipleAuthSets();
        apiInfoList.add(api2);
        SampleDataDao.instance.insertOne(createSampleDataWithBasicAndApiKey());

        ApiInfoDao.instance.insertMany(apiInfoList);

        // Store initial auth types for comparison
        Set<Set<ApiInfo.AuthType>> api1InitialAuth = new HashSet<>(api1.getAllAuthTypesFound());
        Set<Set<ApiInfo.AuthType>> api2InitialAuth = new HashSet<>(api2.getAllAuthTypesFound());

        // Execute: Call calcAuth
        List<WriteModel<ApiInfo>> updates = CustomAuthUtil.calcAuth(apiInfoList, null, true);

        // Assert in-memory state: Should have 2 updates
        assertEquals("Should have 2 WriteModel updates", 2, updates.size());

        // Verify auth types are not empty (either preserved or enhanced)
        assertNotNull("API1 auth types should not be null", api1.getAllAuthTypesFound());
        assertFalse("API1 auth types should not be empty", api1.getAllAuthTypesFound().isEmpty());

        assertNotNull("API2 auth types should not be null", api2.getAllAuthTypesFound());
        assertFalse("API2 auth types should not be empty", api2.getAllAuthTypesFound().isEmpty());

        // Verify existing auth types are preserved
        assertTrue("API1 should preserve all initial auth types",
            api1.getAllAuthTypesFound().containsAll(api1InitialAuth));

        assertTrue("API2 should preserve all initial auth types",
            api2.getAllAuthTypesFound().containsAll(api2InitialAuth));

        // Verify auth types can be enhanced (size >= initial size)
        assertTrue("API1 auth types should be preserved or enhanced",
            api1.getAllAuthTypesFound().size() >= api1InitialAuth.size());

        assertTrue("API2 auth types should be preserved or enhanced",
            api2.getAllAuthTypesFound().size() >= api2InitialAuth.size());

        // Execute bulk write
        if (!updates.isEmpty()) {
            ApiInfoDao.instance.getMCollection().bulkWrite(updates);
        }

        // Assert DB state
        ApiInfo dbApi1 = ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(api1.getId()));
        ApiInfo dbApi2 = ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(api2.getId()));

        assertNotNull("DB API1 should exist", dbApi1);
        assertNotNull("DB API2 should exist", dbApi2);

        assertEquals("In-memory and DB auth types should match for API1",
            api1.getAllAuthTypesFound(),
            dbApi1.getAllAuthTypesFound());

        assertEquals("In-memory and DB auth types should match for API2",
            api2.getAllAuthTypesFound(),
            dbApi2.getAllAuthTypesFound());
    }

    /**
     * Test Group 4: Edge Cases
     * Tests scenarios like missing sample data, no auth headers, multiple samples
     */
    @Test
    public void testCalcAuthEdgeCases() {
        // Clear collections
        ApiInfoDao.instance.getMCollection().drop();
        SampleDataDao.instance.getMCollection().drop();

        // Setup: Create APIs for edge case testing
        List<ApiInfo> apiInfoList = new ArrayList<>();

        // API 1: Has JWT auth, but NO sample data in DB (should be skipped)
        ApiInfo api1 = createApiInfoWithSingleAuthSet();
        api1.getId().setUrl("/api/test/no-sample");
        apiInfoList.add(api1);
        // Intentionally NOT inserting sample data for this API

        // API 2: Has sample data with no auth headers
        ApiInfo api2 = createApiInfoWithNoAuth();
        api2.getId().setUrl("/api/test/no-auth-header");
        apiInfoList.add(api2);
        SampleDataDao.instance.insertOne(createSampleDataWithNoAuth(
            api2.getId().getApiCollectionId(),
            api2.getId().getUrl(),
            api2.getId().getMethod()
        ));

        ApiInfoDao.instance.insertMany(apiInfoList);

        // Store initial state
        Set<Set<ApiInfo.AuthType>> api1InitialAuth = api1.getAllAuthTypesFound() != null
            ? new HashSet<>(api1.getAllAuthTypesFound())
            : null;

        // Execute: Call calcAuth
        List<WriteModel<ApiInfo>> updates = CustomAuthUtil.calcAuth(apiInfoList, null, false);

        // Assert: API without sample data should be skipped
        // Based on line 118 of CustomAuthUtil: "continue" when !sampleProcessed
        // So we expect only 1 update (for api2)
        assertEquals("Should have 1 WriteModel update (api1 skipped)", 1, updates.size());

        // API1 should maintain its initial auth types (unchanged)
        if (api1InitialAuth != null) {
            assertEquals("API1 auth types should be unchanged", api1InitialAuth, api1.getAllAuthTypesFound());
        }

        // Execute bulk write
        if (!updates.isEmpty()) {
            ApiInfoDao.instance.getMCollection().bulkWrite(updates);
        }

        // Assert DB state: api1 should not be in DB (never inserted with updates)
        // api2 should be in DB with detected auth types
        ApiInfo dbApi2 = ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(api2.getId()));
        assertNotNull("DB API2 should exist", dbApi2);
        assertEquals("In-memory and DB auth types should match for API2",
            api2.getAllAuthTypesFound(),
            dbApi2.getAllAuthTypesFound());
    }

    /**
     * Test: Existing Auth Types Preserved and New Ones Added
     * Tests that when an API has existing auth types [[JWT], [BASIC, AUTHORIZATION_HEADER]],
     * and new samples with API_KEY and MTLS are processed, the result is:
     * [[JWT], [BASIC, AUTHORIZATION_HEADER], [API_KEY], [MTLS]]
     */
    @Test
    public void testCalcAuthWithExistingAuthTypesAndNewSamples() {
        // Clear collections
        ApiInfoDao.instance.getMCollection().drop();
        SampleDataDao.instance.getMCollection().drop();

        // Setup: Create API with initial auth types [[JWT], [BASIC, AUTHORIZATION_HEADER]]
        List<ApiInfo> apiInfoList = new ArrayList<>();
        ApiInfo api = createApiInfoForPreserveAndEnhance();
        apiInfoList.add(api);

        // Insert 1 SampleData document with 2 samples: one with API_KEY, one with MTLS
        SampleDataDao.instance.insertOne(createSampleDataWithApiKeyAndMtlsSamples(
            api.getId().getApiCollectionId(),
            api.getId().getUrl(),
            api.getId().getMethod()
        ));

        ApiInfoDao.instance.insertMany(apiInfoList);

        // Store initial auth types for comparison
        Set<Set<ApiInfo.AuthType>> initialAuth = new HashSet<>(api.getAllAuthTypesFound());
        int initialSetCount = initialAuth.size(); // Should be 2

        // Execute: Call calcAuth
        List<WriteModel<ApiInfo>> updates = CustomAuthUtil.calcAuth(apiInfoList, null, false);

        // Assert in-memory state: Should have 1 update
        assertEquals("Should have 1 WriteModel update", 1, updates.size());

        // Verify auth types are not null or empty
        assertNotNull("Auth types should not be null", api.getAllAuthTypesFound());
        assertFalse("Auth types should not be empty", api.getAllAuthTypesFound().isEmpty());

        // Verify all initial auth types are preserved
        assertTrue("Should preserve all initial auth types",
            api.getAllAuthTypesFound().containsAll(initialAuth));

        // Verify new auth types were added
        boolean hasApiKey = api.getAllAuthTypesFound().stream()
            .anyMatch(set -> set.contains(ApiInfo.AuthType.API_KEY));
        assertTrue("Should detect API_KEY from samples", hasApiKey);

        boolean hasMtls = api.getAllAuthTypesFound().stream()
            .anyMatch(set -> set.contains(ApiInfo.AuthType.MTLS));
        assertTrue("Should detect MTLS from samples", hasMtls);

        // Verify we now have 4 sets total (2 initial + 2 new)
        assertEquals("Should have 4 auth type sets total", 4, api.getAllAuthTypesFound().size());

        // Verify the specific sets exist
        Set<ApiInfo.AuthType> jwtSet = new HashSet<>();
        jwtSet.add(ApiInfo.AuthType.JWT);
        assertTrue("Should have [JWT] set", api.getAllAuthTypesFound().contains(jwtSet));

        Set<ApiInfo.AuthType> basicAuthSet = new HashSet<>();
        basicAuthSet.add(ApiInfo.AuthType.BASIC);
        basicAuthSet.add(ApiInfo.AuthType.AUTHORIZATION_HEADER);
        assertTrue("Should have [BASIC, AUTHORIZATION_HEADER] set",
            api.getAllAuthTypesFound().contains(basicAuthSet));

        Set<ApiInfo.AuthType> apiKeySet = new HashSet<>();
        apiKeySet.add(ApiInfo.AuthType.API_KEY);
        assertTrue("Should have [API_KEY] set", api.getAllAuthTypesFound().contains(apiKeySet));

        Set<ApiInfo.AuthType> mtlsSet = new HashSet<>();
        mtlsSet.add(ApiInfo.AuthType.MTLS);
        assertTrue("Should have [MTLS] set", api.getAllAuthTypesFound().contains(mtlsSet));

        // Execute bulk write
        if (!updates.isEmpty()) {
            ApiInfoDao.instance.getMCollection().bulkWrite(updates);
        }

        // Assert DB state
        ApiInfo dbApi = ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(api.getId()));
        assertNotNull("DB API should exist", dbApi);
        assertEquals("In-memory and DB auth types should match",
            api.getAllAuthTypesFound(),
            dbApi.getAllAuthTypesFound());
        assertEquals("DB should have 4 auth type sets", 4, dbApi.getAllAuthTypesFound().size());
    }

}
