package com.akto.dto.traffic;

import com.akto.dto.ApiCollection;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

public class CollectionTagsTest {

    @Test
    public void testCalculateTagsDiffWithUpdatedTags() {
        List<CollectionTags> collectionTagsList = new ArrayList<>();
        collectionTagsList.add(new CollectionTags(123456, "key1", "value1", CollectionTags.TagSource.KUBERNETES));
        collectionTagsList.add(new CollectionTags(123456, "key2", "value2", CollectionTags.TagSource.KUBERNETES));
        collectionTagsList.add(new CollectionTags(123456, "key3", "value3", CollectionTags.TagSource.KUBERNETES));

        String tagsJson = "{\"key1\":\"newValue1\",\"key2\":\"newValue2\"}";

        List<CollectionTags> newTags = CollectionTags.calculateTagsDiff(collectionTagsList, tagsJson);

        assertEquals(2, newTags.size());
        assertEquals("newValue1", newTags.get(0).getValue());
        assertEquals("newValue2", newTags.get(1).getValue());
    }

    @Test
    public void testCalculateTagsDiffWithUpdateAndNewTags() {
        List<CollectionTags> collectionTagsList = new ArrayList<>();
        collectionTagsList.add(new CollectionTags(123456, "key1", "value1", CollectionTags.TagSource.KUBERNETES));

        String tagsJson = "{\"key1\":\"value2\",\"key3\":\"value3\"}";

        List<CollectionTags> newTags = CollectionTags.calculateTagsDiff(collectionTagsList, tagsJson);

        assertEquals(2, newTags.size());
        assertEquals("value2", newTags.get(0).getValue());
        assertEquals("value3", newTags.get(1).getValue());
    }

    @Test
    public void testCalculateTagsDiffWithNewTags() {
        List<CollectionTags> collectionTagsList = new ArrayList<>();
        collectionTagsList.add(new CollectionTags(123456, "key1", "value1", CollectionTags.TagSource.KUBERNETES));

        String tagsJson = "{\"key3\":\"value3\"}";

        List<CollectionTags> newTags = CollectionTags.calculateTagsDiff(collectionTagsList, tagsJson);

        assertEquals(1, newTags.size());
        assertEquals("value3", newTags.get(0).getValue());
    }

    @Test
    public void testCalculateTagsDiffWithNoChanges() {
        List<CollectionTags> collectionTagsList = new ArrayList<>();
        collectionTagsList.add(new CollectionTags(123456, "key1", "value1", CollectionTags.TagSource.KUBERNETES));
        collectionTagsList.add(new CollectionTags(123456, "key2", "value2", CollectionTags.TagSource.KUBERNETES));

        String tagsJson = "{\"key1\":\"value1\",\"key2\":\"value2\"}";

        List<CollectionTags> newTags = CollectionTags.calculateTagsDiff(collectionTagsList, tagsJson);
        assertNull(newTags);
    }

    @Test
    public void testCalculateTagsDiffWithEmptyTagsJson() {
        List<CollectionTags> collectionTagsList = new ArrayList<>();
        collectionTagsList.add(new CollectionTags(123456, "key1", "value1", CollectionTags.TagSource.KUBERNETES));
        collectionTagsList.add(new CollectionTags(123456, "key2", "value2", CollectionTags.TagSource.KUBERNETES));

        String tagsJson = "{}";

        List<CollectionTags> newTags = CollectionTags.calculateTagsDiff(collectionTagsList, tagsJson);

        assertTrue(newTags.isEmpty());
    }

    @Test
    public void testCalculateTagsDiffWithEmptyCollectionTags() {
        List<CollectionTags> collectionTagsList = new ArrayList<>();

        String tagsJson = "{\"key1\":\"value1\",\"key2\":\"value2\"}";

        List<CollectionTags> newTags = CollectionTags.calculateTagsDiff(collectionTagsList, tagsJson);

        assertEquals(2, newTags.size());
        assertEquals("value1", newTags.get(0).getValue());
        assertEquals("value2", newTags.get(1).getValue());
    }

    @Test
    public void testCalculateTagsWithDeletedTags() {
        List<CollectionTags> collectionTagsList = new ArrayList<>();
        collectionTagsList.add(new CollectionTags(123456, "key1", "value1", CollectionTags.TagSource.KUBERNETES));   
        collectionTagsList.add(new CollectionTags(123456, "key2", "value2", CollectionTags.TagSource.KUBERNETES));
        collectionTagsList.add(new CollectionTags(123456, "key3", "value3", CollectionTags.TagSource.KUBERNETES));

        String tagsJson = "{\"key1\":\"value1\"}";
        List<CollectionTags> newTags = CollectionTags.calculateTagsDiff(collectionTagsList, tagsJson);
        assertEquals(1, newTags.size());
        assertEquals("value1", newTags.get(0).getValue());
        assertEquals("key1", newTags.get(0).getKeyName());
    }

    @Test
    public void testCalculateTagsWithNewAndDeletedTags() {
        List<CollectionTags> collectionTagsList = new ArrayList<>();
        collectionTagsList.add(new CollectionTags(123456, "key1", "value1", CollectionTags.TagSource.KUBERNETES));   
        collectionTagsList.add(new CollectionTags(123456, "key2", "value2", CollectionTags.TagSource.KUBERNETES));
        collectionTagsList.add(new CollectionTags(123456, "key3", "value3", CollectionTags.TagSource.KUBERNETES));

        String tagsJson = "{\"key1\":\"value1\",\"key4\":\"value4\"}";
        List<CollectionTags> newTags = CollectionTags.calculateTagsDiff(collectionTagsList, tagsJson);
        assertEquals(2, newTags.size());
        assertEquals("value1", newTags.get(0).getValue());
        assertEquals("key1", newTags.get(0).getKeyName());
        assertEquals("value4", newTags.get(1).getValue());
        assertEquals("key4", newTags.get(1).getKeyName());
    }

    private static ApiCollection collectionWith(String... keys) {
        ApiCollection c = new ApiCollection();
        List<CollectionTags> tags = new ArrayList<>();
        for (String k : keys) {
            tags.add(new CollectionTags(1, k, k + "-value", CollectionTags.TagSource.KUBERNETES));
        }
        c.setTagsList(tags);
        return c;
    }

    /**
     * A request with no pod labels (an istio envoy leg never carries any) must not erase the
     * tags the collection already has - every tag write is a full-array set, so a truncated
     * list here is persisted and the tags are lost for good.
     */
    @Test
    public void testEmptyIncomingTagsKeepsExistingTags() {
        ApiCollection collection = collectionWith("app", "privatecloud.agoda.com/service");

        assertEquals(2, CollectionTags.getUniqueTags(collection, null).size());
        assertEquals(2, CollectionTags.getUniqueTags(collection, new ArrayList<>()).size());
    }

    /** A tagged request must not drop tags the collection has that it does not carry itself. */
    @Test
    public void testIncomingTagsAreUnionedWithExisting() {
        ApiCollection collection = collectionWith("ai-agent-caller");

        List<CollectionTags> incoming = new ArrayList<>();
        incoming.add(new CollectionTags(1, "app", "app-value", CollectionTags.TagSource.KUBERNETES));

        List<CollectionTags> merged = CollectionTags.getUniqueTags(collection, incoming);
        assertEquals(2, merged.size());
        assertTrue(merged.stream().anyMatch(t -> "ai-agent-caller".equals(t.getKeyName())));
        assertTrue(merged.stream().anyMatch(t -> "app".equals(t.getKeyName())));
    }

    /** A collection with no tags yet and nothing incoming stays empty rather than NPE-ing. */
    @Test
    public void testEmptyBothSidesReturnsEmpty() {
        assertTrue(CollectionTags.getUniqueTags(new ApiCollection(), null).isEmpty());
        assertTrue(CollectionTags.getUniqueTags(null, null).isEmpty());
    }
}
