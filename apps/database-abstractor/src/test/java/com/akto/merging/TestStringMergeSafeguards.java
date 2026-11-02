package com.akto.merging;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.type.SingleTypeInfo;
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

import static org.junit.Assert.assertEquals;

/**
 * Templates without STRING (e.g. /pq/INTEGER/BW48) only merge with STRING_MERGING_THRESHOLD other similar URLs with
 * matching params, unless the merged segment is a UUID / alphanumeric id / locale. Static URLs keep the original,
 * looser behaviour; the static* tests pin it so it doesn't change by accident.
 */
public class TestStringMergeSafeguards extends MongoBasedTest {

    private static final int COLLECTION_ID = 7201;
    private static final List<String> CODES = Arrays.asList(
            "BW48", "BC05", "BD12", "BF73", "BG96", "BH48", "BJ05", "BK12", "BL73", "BM96", "BN48", "BP05");

    @Before
    public void clean() {
        Context.accountId.set(ACCOUNT_ID);
        SingleTypeInfoDao.instance.getMCollection().deleteMany(new BasicDBObject());
        SampleDataDao.instance.getMCollection().deleteMany(new BasicDBObject());
        ApiInfoDao.instance.getMCollection().deleteMany(new BasicDBObject());
    }

    private static SingleTypeInfo sti(String url, String param, boolean isHeader, int responseCode) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(url, "GET", responseCode, isHeader, param,
                SingleTypeInfo.GENERIC, COLLECTION_ID, false);
        return new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 1, Context.now(), 0, new CappedSet<>(),
                SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
    }

    private static void insertEndpoint(String url, List<String> reqParams, List<String> respParams) {
        List<SingleTypeInfo> stis = new ArrayList<>();
        stis.add(sti(url, "host", true, -1));
        for (String p : reqParams) stis.add(sti(url, p, false, -1));
        for (String p : respParams) stis.add(sti(url, p, false, 200));
        SingleTypeInfoDao.instance.insertMany(stis);
    }

    private static void insertEndpoint(String url) {
        insertEndpoint(url, Arrays.asList("returnServerResponse"), Arrays.asList("status", "titleId"));
    }

    private static Set<String> mergeAndGetUrls() {
        MergingLogic.mergeUrlsAndSave(COLLECTION_ID, false, false);
        return SingleTypeInfoDao.instance.findAll(Filters.eq("apiCollectionId", COLLECTION_ID)).stream()
                .map(SingleTypeInfo::getUrl).collect(Collectors.toSet());
    }

    private static Set<String> urls(String prefix, List<String> codes) {
        return codes.stream().map(c -> prefix + c).collect(Collectors.toSet());
    }

    private static Set<String> set(String... urls) {
        return new HashSet<>(Arrays.asList(urls));
    }

    // A URL needs STRING_MERGING_THRESHOLD *other* similar URLs, so THRESHOLD URLs in total are not enough
    @Test
    public void thresholdTemplatesAreNotMerged() {
        List<String> codes = CODES.subList(0, MergingLogic.STRING_MERGING_THRESHOLD);
        for (String code : codes) insertEndpoint("/s/INTEGER/" + code);

        assertEquals(urls("/s/INTEGER/", codes), mergeAndGetUrls());
    }

    @Test
    public void thresholdPlusOneTemplatesAreMerged() {
        for (String code : CODES.subList(0, MergingLogic.STRING_MERGING_THRESHOLD + 1)) insertEndpoint("/s/INTEGER/" + code);

        assertEquals(set("/s/INTEGER/STRING"), mergeAndGetUrls());
    }

    @Test
    public void staticUrlsStillMergeBelowThreshold() {
        insertEndpoint("/s/BW48");
        insertEndpoint("/s/BC05");

        assertEquals(set("/s/STRING"), mergeAndGetUrls());
    }

    @Test
    public void twoTemplatesAreNotMerged() {
        insertEndpoint("/nos/INTEGER/acl/addACL");
        insertEndpoint("/nos/INTEGER/acl/deleteACL");

        assertEquals(set("/nos/INTEGER/acl/addACL", "/nos/INTEGER/acl/deleteACL"), mergeAndGetUrls());
    }

    @Test
    public void templatesWithDifferentParamsAreNotMerged() {
        for (int i = 0; i < CODES.size(); i++) {
            insertEndpoint("/s/INTEGER/" + CODES.get(i), Arrays.asList("q" + i), Arrays.asList("r" + i, "s" + i));
        }

        assertEquals(urls("/s/INTEGER/", CODES), mergeAndGetUrls());
    }

    @Test
    public void staticUrlsWithDifferentParamsStillMerge() {
        for (int i = 0; i < CODES.size(); i++) {
            insertEndpoint("/s/" + CODES.get(i), Arrays.asList("q" + i), Arrays.asList("r" + i, "s" + i));
        }

        assertEquals(set("/s/STRING"), mergeAndGetUrls());
    }

    @Test
    public void templateApiVersionsAreNotMergedWhenVersionMergingIsOff() {
        insertEndpoint("/api/v1/invoices/INTEGER/permissions");
        insertEndpoint("/api/v2/invoices/INTEGER/permissions");

        assertEquals(set("/api/v1/invoices/INTEGER/permissions", "/api/v2/invoices/INTEGER/permissions"), mergeAndGetUrls());
    }

    // NWCL/E lines up with many <code>/E siblings, but only NWCL/V and NWCL/M share the NWCL/STRING shape
    @Test
    public void thresholdIsCountedPerTemplate() {
        for (String code : CODES) insertEndpoint("/pq/INTEGER/" + code + "/E");
        for (String variant : Arrays.asList("E", "M", "V")) insertEndpoint("/pq/INTEGER/NWCL/" + variant);

        assertEquals(set("/pq/INTEGER/STRING/E", "/pq/INTEGER/NWCL/M", "/pq/INTEGER/NWCL/V"), mergeAndGetUrls());
    }

    @Test
    public void localeDoesNotVouchForAStringSegment() {
        insertEndpoint("/api/v3/docs/CA/INTEGER");
        insertEndpoint("/api/v10/docs/UK/INTEGER");

        assertEquals(set("/api/v3/docs/CA/INTEGER", "/api/v10/docs/UK/INTEGER"), mergeAndGetUrls());
    }

    @Test
    public void twoTemplateLocalesAreMerged() {
        insertEndpoint("/shop/en/INTEGER/items");
        insertEndpoint("/shop/ja/INTEGER/items");

        assertEquals(set("/shop/LOCALE/INTEGER/items"), mergeAndGetUrls());
    }

    @Test
    public void twoTemplateUuidsAreMerged() {
        insertEndpoint("/u/INTEGER/0f8fad5b-d9cb-469f-a165-70867728950e");
        insertEndpoint("/u/INTEGER/7c9e6679-7425-40de-944b-e07fc1f90ae7");

        assertEquals(set("/u/INTEGER/STRING"), mergeAndGetUrls());
    }

    @Test
    public void twoTemplateAlphanumericIdsAreMerged() {
        insertEndpoint("/a/INTEGER/AB1234");
        insertEndpoint("/a/INTEGER/CD5678");

        assertEquals(set("/a/INTEGER/STRING"), mergeAndGetUrls());
    }

    @Test
    public void twoNumbersAreMerged() {
        insertEndpoint("/n/1");
        insertEndpoint("/n/2");

        assertEquals(set("/n/INTEGER"), mergeAndGetUrls());
    }
}
