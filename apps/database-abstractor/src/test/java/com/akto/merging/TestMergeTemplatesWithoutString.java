package com.akto.merging;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLTemplate;
import com.akto.types.CappedSet;
import com.akto.utils.MongoBasedTest;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * For TEMPLATE_MERGING_ACCOUNT_ID, templates without a STRING segment (e.g. /api/v1/titleProcurement2/INTEGER/BW48)
 * are merged with each other, needing STRING_MERGING_THRESHOLD other similar URLs unless the merged segment is a
 * UUID / alphanumeric id / locale. Existing templates still only absorb static URLs, and other accounts are unaffected.
 */
public class TestMergeTemplatesWithoutString extends MongoBasedTest {

    private static final int COLLECTION_ID = 7101;
    private static final String PREFIX = "/api/v1/titleProcurement2/INTEGER/";
    private static final List<String> CODES = Arrays.asList(
            "BW48", "BRDS", "BOG2", "KBCA", "SCGI", "ICNH", "BRC1", "PLM1", "SH05", "SWRE", "KTCI", "SGA2");

    @Before
    public void clean() {
        clearAccount(ACCOUNT_ID);
        clearAccount(MergingLogic.TEMPLATE_MERGING_ACCOUNT_ID);
    }

    @After
    public void resetAccount() {
        Context.accountId.set(ACCOUNT_ID);
    }

    // leaves Context.accountId set to the given account
    private static void clearAccount(int accountId) {
        Context.accountId.set(accountId);
        SingleTypeInfoDao.instance.getMCollection().deleteMany(new BasicDBObject());
        SampleDataDao.instance.getMCollection().deleteMany(new BasicDBObject());
        ApiInfoDao.instance.getMCollection().deleteMany(new BasicDBObject());
    }

    private static SingleTypeInfo sti(String url, String param, boolean isHeader, int responseCode, boolean isUrlParam) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(url, "GET", responseCode, isHeader, param,
                SingleTypeInfo.GENERIC, COLLECTION_ID, isUrlParam);
        return new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 1, Context.now(), 0, new CappedSet<>(),
                SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
    }

    // Same params for every endpoint, like the runtime would record for one API with a varying code
    private static void insertEndpoint(String url) {
        SingleTypeInfoDao.instance.insertMany(Arrays.asList(
                sti(url, "host", true, -1, false),
                sti(url, "3", false, -1, true),
                sti(url, "returnServerResponse", false, -1, false),
                sti(url, "status", false, 200, false),
                sti(url, "titleId", false, 200, false)));
    }

    private static Set<String> urlsInDb() {
        return SingleTypeInfoDao.instance.findAll(Filters.eq("apiCollectionId", COLLECTION_ID)).stream()
                .map(SingleTypeInfo::getUrl).collect(Collectors.toSet());
    }

    private static long stiCount(String url) {
        return SingleTypeInfoDao.instance.count(Filters.and(Filters.eq("apiCollectionId", COLLECTION_ID), Filters.eq("url", url)));
    }

    private static Set<String> set(String... urls) {
        return new HashSet<>(Arrays.asList(urls));
    }

    private static Set<String> codeUrls() {
        return CODES.stream().map(c -> PREFIX + c).collect(Collectors.toSet());
    }

    private static URLTemplate tryMerge(String a, String b) {
        return MergingLogic.tryMergeUrls(new URLStatic(a, URLMethods.Method.GET), new URLStatic(b, URLMethods.Method.GET), false, true);
    }

    @Test
    public void tryMergeUrls_mergesTheStaticSegmentOfIntegerTemplates() {
        URLTemplate merged = tryMerge(PREFIX + "BW48", PREFIX + "BRDS");

        assertNotNull(merged);
        assertEquals(PREFIX + "STRING", merged.getTemplateString());
        // only the new position is typed, so mergeUrlsAndSave adds a url-param STI just for it
        assertNull(merged.getTypes()[3]);
        assertEquals(SingleTypeInfo.SuperType.STRING, merged.getTypes()[4]);
    }

    @Test
    public void tryMergeUrls_placeholderOnlyLinesUpWithTheSamePlaceholder() {
        assertNull(tryMerge(PREFIX + "BW48", "/api/v1/titleProcurement2/123/BW48"));
        assertNull(tryMerge(PREFIX + "BW48", "/api/v1/titleProcurement2/integer/BRDS"));
        assertNull(tryMerge(PREFIX + "BW48", "/api/v1/titleProcurement2/UUID/BRDS"));
        assertNull(tryMerge("/api/UUID/items", "/api/OBJECT_ID/items"));
        assertNull(tryMerge("/orders/UUID/x", "/orders/ab12cd/x"));
        assertNull(tryMerge("/api/VERSIONED/users", "/api/v2/users"));
        assertEquals("/a/UUID/STRING", tryMerge("/a/UUID/ab12", "/a/UUID/cd34").getTemplateString());
    }

    @Test
    public void tryMergeUrls_unchangedForOtherAccounts() {
        Context.accountId.set(ACCOUNT_ID);
        assertEquals("/api/STRING/items", tryMerge("/api/UUID/items", "/api/OBJECT_ID/items").getTemplateString());
    }

    @Test
    public void integerTemplatesAreMergedIntoOneStringTemplate() {
        for (String code : CODES) insertEndpoint(PREFIX + code);

        MergingLogic.ApiMergerResult result = MergingLogic.tryMergeURLsInCollection(COLLECTION_ID, false, false);

        assertEquals(1, result.templateToStaticURLs.size());
        URLTemplate template = result.templateToStaticURLs.keySet().iterator().next();
        assertEquals(PREFIX + "STRING", template.getTemplateString());
        assertEquals(CODES.size(), result.templateToStaticURLs.get(template).size());
        assertEquals(set(), result.deleteStaticUrls);

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(set(PREFIX + "STRING"), urlsInDb());
        // url-param STIs: the renamed INTEGER one plus one for the new STRING position
        Set<String> urlParams = SingleTypeInfoDao.instance.findAll(Filters.and(
                Filters.eq("apiCollectionId", COLLECTION_ID), Filters.eq("isUrlParam", true))).stream()
                .map(SingleTypeInfo::getParam).collect(Collectors.toSet());
        assertEquals(set("3", "4"), urlParams);
    }

    @Test
    public void otherAccountsLeaveTemplatesAlone() {
        Context.accountId.set(ACCOUNT_ID);
        for (String code : CODES) insertEndpoint(PREFIX + code);

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(codeUrls(), urlsInDb());
    }

    @Test
    public void templatesWithStringAreNotMerged() {
        for (String code : CODES) insertEndpoint("/api/STRING/" + code);

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(CODES.stream().map(c -> "/api/STRING/" + c).collect(Collectors.toSet()), urlsInDb());
    }

    @Test
    public void noStringMergingInBasicMode() {
        for (String code : CODES) insertEndpoint(PREFIX + code);

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, true, false);

        assertEquals(codeUrls(), urlsInDb());
    }

    // A URL needs STRING_MERGING_THRESHOLD *other* similar URLs; static URLs keep merging with just two
    @Test
    public void thresholdTemplatesAreNotMerged() {
        List<String> codes = CODES.subList(0, MergingLogic.STRING_MERGING_THRESHOLD);
        for (String code : codes) insertEndpoint(PREFIX + code);
        insertEndpoint("/s/BW48");
        insertEndpoint("/s/BRDS");

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        Set<String> expected = codes.stream().map(c -> PREFIX + c).collect(Collectors.toSet());
        expected.add("/s/STRING");
        assertEquals(expected, urlsInDb());
    }

    @Test
    public void thresholdPlusOneTemplatesAreMerged() {
        for (String code : CODES.subList(0, MergingLogic.STRING_MERGING_THRESHOLD + 1)) insertEndpoint(PREFIX + code);

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(set(PREFIX + "STRING"), urlsInDb());
    }

    @Test
    public void templatesWithDifferentParamsAreNotMerged() {
        for (int i = 0; i < CODES.size(); i++) {
            String url = PREFIX + CODES.get(i);
            SingleTypeInfoDao.instance.insertMany(Arrays.asList(
                    sti(url, "host", true, -1, false), sti(url, "q" + i, false, -1, false), sti(url, "r" + i, false, 200, false)));
        }

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(codeUrls(), urlsInDb());
    }

    // NWCL/E lines up with many <code>/E siblings, but only NWCL/V and NWCL/M share the NWCL/STRING shape
    @Test
    public void thresholdIsCountedPerTemplate() {
        for (String code : CODES) insertEndpoint("/pq/INTEGER/" + code + "/E");
        for (String variant : Arrays.asList("E", "M", "V")) insertEndpoint("/pq/INTEGER/NWCL/" + variant);

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(set("/pq/INTEGER/STRING/E", "/pq/INTEGER/NWCL/M", "/pq/INTEGER/NWCL/V"), urlsInDb());
    }

    @Test
    public void localeDoesNotVouchForAStringSegment() {
        insertEndpoint("/api/v3/docs/CA/INTEGER");
        insertEndpoint("/api/v10/docs/UK/INTEGER");

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(set("/api/v3/docs/CA/INTEGER", "/api/v10/docs/UK/INTEGER"), urlsInDb());
    }

    @Test
    public void twoTemplateLocalesAreMerged() {
        insertEndpoint("/shop/en/INTEGER/items");
        insertEndpoint("/shop/ja/INTEGER/items");

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(set("/shop/LOCALE/INTEGER/items"), urlsInDb());
    }

    @Test
    public void twoTemplateApiVersionsAreNotMergedWhenVersionMergingIsOff() {
        insertEndpoint("/api/v1/invoices/INTEGER/permissions");
        insertEndpoint("/api/v2/invoices/INTEGER/permissions");

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(set("/api/v1/invoices/INTEGER/permissions", "/api/v2/invoices/INTEGER/permissions"), urlsInDb());
    }

    @Test
    public void twoTemplateAlphanumericIdsAreMerged() {
        insertEndpoint("/a/INTEGER/AB1234");
        insertEndpoint("/a/INTEGER/CD5678");

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(set("/a/INTEGER/STRING"), urlsInDb());
    }

    @Test
    public void templateDoesNotDeleteItself() {
        insertEndpoint(PREFIX + "BW48");
        insertEndpoint("/api/v1/titleProcurement2/INTEGER/BRDS/details");

        MergingLogic.ApiMergerResult result = MergingLogic.tryMergeURLsInCollection(COLLECTION_ID, false, false);
        assertEquals(set(), result.deleteStaticUrls);
        assertTrue(result.templateToStaticURLs.isEmpty());

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(set(PREFIX + "BW48", "/api/v1/titleProcurement2/INTEGER/BRDS/details"), urlsInDb());
        assertEquals(5, stiCount(PREFIX + "BW48"));
    }

    @Test
    public void existingTemplateDoesNotAbsorbATemplate() {
        insertEndpoint(PREFIX + "STRING");
        insertEndpoint(PREFIX + "BW99");

        MergingLogic.ApiMergerResult result = MergingLogic.tryMergeURLsInCollection(COLLECTION_ID, false, false);
        assertEquals(set(), result.deleteStaticUrls);

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(set(PREFIX + "STRING", PREFIX + "BW99"), urlsInDb());
        assertEquals(5, stiCount(PREFIX + "BW99"));
    }

    @Test
    public void leftoversAboveThresholdMergeIntoExistingTemplate() {
        insertEndpoint(PREFIX + "STRING");
        for (String code : CODES) insertEndpoint(PREFIX + code);

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(set(PREFIX + "STRING"), urlsInDb());
    }

    @Test
    public void existingTemplateStillAbsorbsStaticUrls() {
        insertEndpoint("/u/INTEGER");
        insertEndpoint("/u/123");

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(set("/u/INTEGER"), urlsInDb());
    }

    @Test
    public void templatesAndStaticUrlsDoNotMerge() {
        insertEndpoint(PREFIX + "BW48");
        insertEndpoint("/api/v1/titleProcurement2/ab12cd/BRDS");

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(set(PREFIX + "BW48", "/api/v1/titleProcurement2/ab12cd/BRDS"), urlsInDb());
    }

    @Test
    public void differentPlaceholdersAreNotMerged() {
        insertEndpoint("/api/UUID/items");
        insertEndpoint("/api/OBJECT_ID/items");

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(set("/api/UUID/items", "/api/OBJECT_ID/items"), urlsInDb());
    }

    @Test
    public void caseVariantsAreLeftAlone() {
        insertEndpoint(PREFIX + "ab12");
        insertEndpoint(PREFIX + "AB12");

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(set(PREFIX + "ab12", PREFIX + "AB12"), urlsInDb());
        assertEquals(5, stiCount(PREFIX + "ab12"));
        assertEquals(5, stiCount(PREFIX + "AB12"));
    }

    @Test
    public void caseVariantsStillMergeWithRealSiblings() {
        for (String code : CODES) insertEndpoint(PREFIX + code);
        insertEndpoint(PREFIX + "bw48");

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(set(PREFIX + "STRING"), urlsInDb());
    }

    // An upper-case variant of the path yields a second template (/LOTS/... vs /lots/...); its siblings could line up
    // with both. Each URL must end up in one merge group only, or its STIs are renamed for one and deleted for the other.
    @Test
    public void urlIsPutInOneMergeGroupOnly() {
        for (String code : CODES) insertEndpoint("/" + code + "/lots/details/INTEGER/tp2");
        insertEndpoint("/NC12/LOTS/DETAILS/INTEGER/TP2");
        // numbers and codes at the same position line up as INTEGER and as STRING
        for (int i = 0; i < 12; i++) insertEndpoint("/m/x/" + (600 + i) + "/INTEGER");
        for (String code : CODES) insertEndpoint("/m/x/" + code + "/INTEGER");

        MergingLogic.ApiMergerResult result = MergingLogic.tryMergeURLsInCollection(COLLECTION_ID, false, false);
        Set<String> seen = new HashSet<>();
        for (Set<String> members : result.templateToStaticURLs.values()) {
            for (String member : members) assertTrue("in two merge groups: " + member, seen.add(member));
        }

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        for (String url : urlsInDb()) {
            long params = SingleTypeInfoDao.instance.count(Filters.and(Filters.eq("apiCollectionId", COLLECTION_ID),
                    Filters.eq("url", url), Filters.eq("isUrlParam", false)));
            assertEquals("params of " + url, 4, params);
        }
    }
}
