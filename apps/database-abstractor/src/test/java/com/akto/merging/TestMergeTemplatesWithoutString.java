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
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Templates without a STRING segment (e.g. /api/v1/titleProcurement2/INTEGER/BW48) take part in
 * static-to-static merging, and get absorbed by an existing template of the same shape.
 */
public class TestMergeTemplatesWithoutString extends MongoBasedTest {

    private static final int COLLECTION_ID = 7101;
    private static final String PREFIX = "/api/v1/titleProcurement2/INTEGER/";
    private static final List<String> CODES = Arrays.asList(
            "BW48", "BRDS", "BOG2", "KBCA", "SCGI", "ICNH", "BRC1", "PLM1", "SH05", "SWRE", "KTCI", "SGA2");

    @Before
    public void clean() {
        Context.accountId.set(ACCOUNT_ID);
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

    @Test
    public void tryMergeUrls_mergesTheStaticSegmentOfIntegerTemplates() {
        URLTemplate merged = MergingLogic.tryMergeUrls(
                new URLStatic(PREFIX + "BW48", URLMethods.Method.GET),
                new URLStatic(PREFIX + "BRDS", URLMethods.Method.GET), false, true);

        assertNotNull(merged);
        assertEquals(PREFIX + "STRING", merged.getTemplateString());
        // only the new position is typed, so mergeUrlsAndSave adds a url-param STI just for it
        assertNull(merged.getTypes()[3]);
        assertEquals(SingleTypeInfo.SuperType.STRING, merged.getTypes()[4]);
    }

    @Test
    public void tryMergeUrls_placeholderNeverMergesWithADifferentSegment() {
        assertNull(MergingLogic.tryMergeUrls(
                new URLStatic(PREFIX + "BW48", URLMethods.Method.GET),
                new URLStatic("/api/v1/titleProcurement2/123/BW48", URLMethods.Method.GET), false, true));
        assertNull(MergingLogic.tryMergeUrls(
                new URLStatic(PREFIX + "BW48", URLMethods.Method.GET),
                new URLStatic("/api/v1/titleProcurement2/UUID/BW48", URLMethods.Method.GET), false, true));
    }

    @Test
    public void matchesTemplate_placeholderMatchesSameType() {
        URLTemplate template = MergingLogic.createUrlTemplate(PREFIX + "STRING", URLMethods.Method.GET);

        assertTrue(MergingLogic.matchesTemplate(template, PREFIX + "NEW1", URLMethods.Method.GET));
        assertTrue(MergingLogic.matchesTemplate(template, "/api/v1/titleProcurement2/123/NEW1", URLMethods.Method.GET));
        assertFalse(MergingLogic.matchesTemplate(template, PREFIX + "NEW1", URLMethods.Method.POST));
        assertFalse(MergingLogic.matchesTemplate(template, "/api/v1/other/INTEGER/NEW1", URLMethods.Method.GET));
        assertFalse(MergingLogic.matchesTemplate(template, PREFIX + "NEW1/extra", URLMethods.Method.GET));

        URLTemplate localeTemplate = MergingLogic.createUrlTemplate("/shop/LOCALE/INTEGER", URLMethods.Method.GET);
        assertFalse(MergingLogic.matchesTemplate(localeTemplate, "/shop/LOCALE/abc", URLMethods.Method.GET));
    }

    @Test
    public void integerTemplatesAreMergedIntoOneStringTemplate() {
        for (String code : CODES) insertEndpoint(PREFIX + code);

        MergingLogic.ApiMergerResult result = MergingLogic.tryMergeURLsInCollection(COLLECTION_ID, false, false);

        assertEquals(1, result.templateToStaticURLs.size());
        URLTemplate template = result.templateToStaticURLs.keySet().iterator().next();
        assertEquals(PREFIX + "STRING", template.getTemplateString());
        assertEquals(CODES.size(), result.templateToStaticURLs.get(template).size());
        assertEquals(new HashSet<String>(), result.deleteStaticUrls);

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(new HashSet<>(Arrays.asList(PREFIX + "STRING")), urlsInDb());
        // url-param STIs: the renamed INTEGER one plus one for the new STRING position
        Set<String> urlParams = SingleTypeInfoDao.instance.findAll(Filters.and(
                Filters.eq("apiCollectionId", COLLECTION_ID), Filters.eq("isUrlParam", true))).stream()
                .map(SingleTypeInfo::getParam).collect(Collectors.toSet());
        assertEquals(new HashSet<>(Arrays.asList("3", "4")), urlParams);
    }

    @Test
    public void belowThresholdIsNotMerged() {
        List<String> codes = CODES.subList(0, MergingLogic.STRING_MERGING_THRESHOLD);
        for (String code : codes) insertEndpoint(PREFIX + code);

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(codes.stream().map(c -> PREFIX + c).collect(Collectors.toSet()), urlsInDb());
    }

    @Test
    public void differentParamsAreNotMerged() {
        for (int i = 0; i < CODES.size(); i++) {
            String url = PREFIX + CODES.get(i);
            SingleTypeInfoDao.instance.insertMany(Arrays.asList(
                    sti(url, "host", true, -1, false), sti(url, "q" + i, false, -1, false), sti(url, "r" + i, false, 200, false)));
        }

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(CODES.stream().map(c -> PREFIX + c).collect(Collectors.toSet()), urlsInDb());
    }

    @Test
    public void noStringMergingInBasicMode() {
        for (String code : CODES) insertEndpoint(PREFIX + code);

        MergingLogic.ApiMergerResult result = MergingLogic.tryMergeURLsInCollection(COLLECTION_ID, true, false);

        assertEquals(new java.util.HashMap<URLTemplate, Set<String>>(), result.templateToStaticURLs);
        assertEquals(new HashSet<String>(), result.deleteStaticUrls);
    }

    @Test
    public void leftoverIsAbsorbedByExistingTemplate() {
        insertEndpoint(PREFIX + "STRING");
        insertEndpoint(PREFIX + "NEW1");

        MergingLogic.ApiMergerResult result = MergingLogic.tryMergeURLsInCollection(COLLECTION_ID, false, false);

        assertEquals(new HashSet<>(Arrays.asList("GET " + PREFIX + "NEW1")), result.deleteStaticUrls);

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(new HashSet<>(Arrays.asList(PREFIX + "STRING")), urlsInDb());
    }

    @Test
    public void templateIsNotMatchedAgainstItself() {
        insertEndpoint(PREFIX + "BW48");
        insertEndpoint("/api/v1/titleProcurement2/INTEGER/BRDS/details");

        MergingLogic.ApiMergerResult result = MergingLogic.tryMergeURLsInCollection(COLLECTION_ID, false, false);

        assertEquals(new HashSet<String>(), result.deleteStaticUrls);
        assertEquals(new java.util.HashMap<URLTemplate, Set<String>>(), result.templateToStaticURLs);

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(new HashSet<>(Arrays.asList(PREFIX + "BW48", "/api/v1/titleProcurement2/INTEGER/BRDS/details")), urlsInDb());
    }

    @Test
    public void caseVariantsAreLeftAlone() {
        insertEndpoint(PREFIX + "ab12");
        insertEndpoint(PREFIX + "AB12");

        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);

        assertEquals(new HashSet<>(Arrays.asList(PREFIX + "ab12", PREFIX + "AB12")), urlsInDb());
    }

    @Test
    public void optimizedMatcherAbsorbsLeftoverButNotItself() {
        List<String> templates = new ArrayList<>(Arrays.asList("GET " + PREFIX + "STRING", "GET " + PREFIX + "BW48"));
        java.util.Map<String, Set<String>> statics = new java.util.HashMap<>();
        statics.put("GET " + PREFIX + "BW48", new HashSet<>());
        statics.put("GET " + PREFIX + "NEW1", new HashSet<>());

        MergingLogic.ApiMergerResult result = new MergingLogic.ApiMergerResult(new java.util.HashMap<>());
        MergingLogic.optimizedTemplateToStaticMatch(templates, statics, COLLECTION_ID, result);

        // BW48 is absorbed by INTEGER/STRING (never by its own entry); NEW1 too
        assertEquals(new HashSet<>(Arrays.asList("GET " + PREFIX + "BW48", "GET " + PREFIX + "NEW1")), result.deleteStaticUrls);
        assertEquals(new HashSet<>(Arrays.asList("GET " + PREFIX + "STRING")), result.existingTemplateMatches.keySet());
    }
}
