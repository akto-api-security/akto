package com.akto.action;

import com.akto.dto.ApiCollection;
import com.akto.listener.RuntimeListener;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class TestResolveUrlsCount {

    private ApiCollection collection(int id, String hostName, ApiCollection.Type type, Set<String> urls) {
        ApiCollection c = new ApiCollection();
        c.setId(id);
        c.setHostName(hostName);
        c.setType(type);
        c.setUrls(urls);
        return c;
    }

    private Set<String> urls(int n) {
        Set<String> s = new HashSet<>();
        for (int i = 0; i < n; i++) s.add("u" + i);
        return s;
    }

    @Test
    public void hostCollectionWithCountUsesCount() {
        ApiCollection c = collection(5, "a.com", null, urls(3));
        assertEquals(10, ApiCollectionsAction.resolveUrlsCount(c, 10));
    }

    @Test
    public void hostCollectionWithoutCountFallsBackToUrlsSize() {
        ApiCollection c = collection(5, "a.com", null, urls(3));
        assertEquals(3, ApiCollectionsAction.resolveUrlsCount(c, null));
    }

    @Test
    public void groupUsesCountElseFallback() {
        ApiCollection c = collection(5, null, ApiCollection.Type.API_GROUP, urls(4));
        assertEquals(9, ApiCollectionsAction.resolveUrlsCount(c, 9));
        assertEquals(4, ApiCollectionsAction.resolveUrlsCount(c, null));
    }

    @Test
    public void nonHostCollectionPrefersFallbackUnlessZero() {
        ApiCollection c = collection(5, null, null, urls(3));
        assertEquals(3, ApiCollectionsAction.resolveUrlsCount(c, 10));
        ApiCollection empty = collection(6, null, null, urls(0));
        assertEquals(10, ApiCollectionsAction.resolveUrlsCount(empty, 10));
        assertEquals(0, ApiCollectionsAction.resolveUrlsCount(empty, null));
    }

    @Test
    public void defaultCollectionUsesCountWhenPresent() {
        ApiCollection c = collection(0, null, null, urls(3));
        assertEquals(10, ApiCollectionsAction.resolveUrlsCount(c, 10));
    }

    @Test
    public void vulnerableCollectionFallsBackTo200() {
        ApiCollection c = collection(RuntimeListener.VULNERABLE_API_COLLECTION_ID, null, null, urls(3));
        assertEquals(200, ApiCollectionsAction.resolveUrlsCount(c, null));
    }
}
