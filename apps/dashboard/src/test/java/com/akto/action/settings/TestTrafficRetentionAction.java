package com.akto.action.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;

public class TestTrafficRetentionAction extends MongoBasedTest {

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(null);
        ApiCollectionsDao.instance.getMCollection().drop();
    }

    @Test
    public void previewWithDaysBelowFloorIsRejectedWithAnActionError() {
        TrafficRetentionAction action = new TrafficRetentionAction();
        action.setDays(7);

        assertEquals("ERROR", action.previewTrafficRetentionCleanup());
        assertFalse(action.getActionErrors().isEmpty());
        assertEquals(null, action.getPreview());
    }

    @Test
    public void previewWithNoEligibleCollectionsIsCompleteAndEmpty() {
        TrafficRetentionAction action = new TrafficRetentionAction();
        action.setDays(180);

        assertEquals("SUCCESS", action.previewTrafficRetentionCleanup());
        assertTrue(action.getPreview().isComplete());
        assertEquals(0, action.getPreview().getTotalStaleApis());
        assertTrue(action.getPreview().getCollections().isEmpty());
    }

    @Test
    public void previewListsAnEligibleCollectionWithItsCount() {
        ApiCollectionsDao.instance.insertOne(new ApiCollection(30, "c30", 0, new HashSet<>(), "h.com", 0, false, true));
        TrafficRetentionAction action = new TrafficRetentionAction();
        action.setDays(365);

        assertEquals("SUCCESS", action.previewTrafficRetentionCleanup());
        assertEquals(1, action.getPreview().getCollections().size());
        assertEquals(30, action.getPreview().getCollections().get(0).getApiCollectionId());
        assertEquals(Integer.valueOf(0), action.getPreview().getCollections().get(0).getStaleApis());
    }

    @Test
    public void startWithCutoffYoungerThanTheFloorIsRejectedAndDoesNotStart() {
        TrafficRetentionAction action = new TrafficRetentionAction();
        action.setCutoffEpoch(Context.now());

        assertEquals("ERROR", action.startTrafficRetentionCleanup());
        assertFalse(action.isStarted());
        assertFalse(action.getActionErrors().isEmpty());
    }

    @Test
    public void startWithMissingCutoffIsRejected() {
        TrafficRetentionAction action = new TrafficRetentionAction();

        assertEquals("ERROR", action.startTrafficRetentionCleanup());
        assertFalse(action.isStarted());
    }
}
