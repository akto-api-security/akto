package com.akto.dto.traffic;

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
        collectionTagsList.add(new CollectionTags(123456, "key1", "value1"));
        collectionTagsList.add(new CollectionTags(123456, "key2", "value2"));
        collectionTagsList.add(new CollectionTags(123456, "key3", "value3"));

        String tagsJson = "{\"key1\":\"newValue1\",\"key2\":\"newValue2\"}";

        List<CollectionTags> newTags = CollectionTags.getUpdatedTagsForCollection(collectionTagsList, tagsJson);

        assertEquals(2, newTags.size());
        assertEquals("newValue1", newTags.get(0).getValue());
        assertEquals("newValue2", newTags.get(1).getValue());
    }

    @Test
    public void testCalculateTagsDiffWithUpdateAndNewTags() {
        List<CollectionTags> collectionTagsList = new ArrayList<>();
        collectionTagsList.add(new CollectionTags(123456, "key1", "value1"));

        String tagsJson = "{\"key1\":\"value2\",\"key3\":\"value3\"}";

        List<CollectionTags> newTags = CollectionTags.getUpdatedTagsForCollection(collectionTagsList, tagsJson);

        assertEquals(2, newTags.size());
        assertEquals("value2", newTags.get(0).getValue());
        assertEquals("value3", newTags.get(1).getValue());
    }

    @Test
    public void testCalculateTagsDiffWithNewTags() {
        List<CollectionTags> collectionTagsList = new ArrayList<>();
        collectionTagsList.add(new CollectionTags(123456, "key1", "value1"));

        String tagsJson = "{\"key3\":\"value3\"}";

        List<CollectionTags> newTags = CollectionTags.getUpdatedTagsForCollection(collectionTagsList, tagsJson);

        assertEquals(1, newTags.size());
        assertEquals("value3", newTags.get(0).getValue());
    }

    @Test
    public void testCalculateTagsDiffWithNoChanges() {
        List<CollectionTags> collectionTagsList = new ArrayList<>();
        collectionTagsList.add(new CollectionTags(123456, "key1", "value1"));
        collectionTagsList.add(new CollectionTags(123456, "key2", "value2"));

        String tagsJson = "{\"key1\":\"value1\",\"key2\":\"value2\"}";

        List<CollectionTags> newTags = CollectionTags.getUpdatedTagsForCollection(collectionTagsList, tagsJson);
        assertNull(newTags);
    }

    @Test
    public void testCalculateTagsDiffWithEmptyTagsJson() {
        List<CollectionTags> collectionTagsList = new ArrayList<>();
        collectionTagsList.add(new CollectionTags(123456, "key1", "value1"));
        collectionTagsList.add(new CollectionTags(123456, "key2", "value2"));

        String tagsJson = "{}";

        List<CollectionTags> newTags = CollectionTags.getUpdatedTagsForCollection(collectionTagsList, tagsJson);

        assertTrue(newTags.isEmpty());
    }

    @Test
    public void testCalculateTagsDiffWithEmptyCollectionTags() {
        List<CollectionTags> collectionTagsList = new ArrayList<>();

        String tagsJson = "{\"key1\":\"value1\",\"key2\":\"value2\"}";

        List<CollectionTags> newTags = CollectionTags.getUpdatedTagsForCollection(collectionTagsList, tagsJson);

        assertEquals(2, newTags.size());
        assertEquals("value1", newTags.get(0).getValue());
        assertEquals("value2", newTags.get(1).getValue());
    }

    @Test
    public void testCalculateTagsWithDeletedTags() {
        List<CollectionTags> collectionTagsList = new ArrayList<>();
        collectionTagsList.add(new CollectionTags(123456, "key1", "value1"));   
        collectionTagsList.add(new CollectionTags(123456, "key2", "value2"));
        collectionTagsList.add(new CollectionTags(123456, "key3", "value3"));

        String tagsJson = "{\"key1\":\"value1\"}";
        List<CollectionTags> newTags = CollectionTags.getUpdatedTagsForCollection(collectionTagsList, tagsJson);
        assertEquals(1, newTags.size());
        assertEquals("value1", newTags.get(0).getValue());
        assertEquals("key1", newTags.get(0).getKeyName());
    }

    @Test
    public void testCalculateTagsWithNewAndDeletedTags() {
        List<CollectionTags> collectionTagsList = new ArrayList<>();
        collectionTagsList.add(new CollectionTags(123456, "key1", "value1"));   
        collectionTagsList.add(new CollectionTags(123456, "key2", "value2"));
        collectionTagsList.add(new CollectionTags(123456, "key3", "value3"));

        String tagsJson = "{\"key1\":\"value1\",\"key4\":\"value4\"}";
        List<CollectionTags> newTags = CollectionTags.getUpdatedTagsForCollection(collectionTagsList, tagsJson);
        assertEquals(2, newTags.size());
        assertEquals("value1", newTags.get(0).getValue());
        assertEquals("key1", newTags.get(0).getKeyName());
        assertEquals("value4", newTags.get(1).getValue());
        assertEquals("key4", newTags.get(1).getKeyName());
    }
}