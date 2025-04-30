package com.akto.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mockStatic;
import java.util.*;

import org.junit.Test;
import org.mockito.MockedStatic;
import com.akto.MongoBasedTest;
import com.akto.dao.UsersDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dto.User;
import com.akto.dto.UserAccountEntry;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.listener.InitializerListener;
import com.akto.utils.billing.OrganizationUtils;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

public class TestProfileExpired extends MongoBasedTest {

    private static Organization createAndInsertOrg() {
        Map<String, UserAccountEntry> accounts = new HashMap<>();
        accounts.put(ACCOUNT_ID + "", new UserAccountEntry(ACCOUNT_ID));
        UsersDao.instance.insertOne(new User("test", "test@akto.io", accounts, null));

        User user = UsersDao.instance.findOne(new BasicDBObject());

        Organization org = new Organization(UUID.randomUUID().toString(), user.getLogin(), user.getLogin(),
                new HashSet<>(Arrays.asList(ACCOUNT_ID)), true);
        OrganizationsDao.instance.insertOne(org);
        return org;
    }

    @Test
    public void testExpiredForAktoUnreachable() throws InterruptedException {

        OrganizationsDao.instance.getMCollection().drop();
        UsersDao.instance.getMCollection().drop();
        Organization org = createAndInsertOrg();

        try (MockedStatic<OrganizationUtils> orgUtils = mockStatic(OrganizationUtils.class)) {
            String id = org.getId();
            String email = org.getAdminEmail();
            // this mimics a situation when akto-usage-service is unreachable
            orgUtils.when(() -> OrganizationUtils.fetchEntitlements(id, email)).thenReturn(null);
            // initially when org is created
            assertEquals(0, org.getLastFeatureMapUpdate());
            assertEquals(false, org.checkExpirationWithAktoSync());
            org = InitializerListener.fetchAndSaveFeatureWiseAllowed(org);
            /*
             * lastUpdateTimestamp is always updated the first time,
             * irrespective of akto-usage-service's reachability.
             */
            assertNotEquals(0, org.getLastFeatureMapUpdate());
            assertEquals(false, org.checkExpirationWithAktoSync());
            /*
             * since we cache the result for every minute,
             * we set the lastUpdateTimestamp before 2 mins. to stimulate the function
             */
            int temp = Context.now() - 2 * 60;
            org.setLastFeatureMapUpdate(temp);
            org = InitializerListener.fetchAndSaveFeatureWiseAllowed(org);
            /*
             * If akto-usage-service is unreachable the
             * lastUpdateTimestamp remains the same.
             */
            assertEquals(temp, org.getLastFeatureMapUpdate());

            // If akto-usage-service is unreachable for more than NO_SYNC_PERIOD
            org.setLastFeatureMapUpdate(Context.now() - Organization.NO_SYNC_PERIOD - 60);
            // TODO: update this when no connectivity check is enabled.
            assertEquals(false, org.checkExpirationWithAktoSync());
        }
    }

    @Test
    public void testExpiredForAktoReachable() throws InterruptedException {

        OrganizationsDao.instance.getMCollection().drop();
        UsersDao.instance.getMCollection().drop();
        Organization org = createAndInsertOrg();

        BasicDBObject sampleFeature = new BasicDBObject()
                .append("isGranted", true)
                .append("feature",
                        new BasicDBObject().append("additionalMetaData",
                                new BasicDBObject().append("key", "SAMPLE_FEATURE")));

        BasicDBList featureList = new BasicDBList();
        featureList.add(sampleFeature);

        HashMap<String, FeatureAccess> featureWiseAllowed = new HashMap<>();
        featureWiseAllowed.put("SAMPLE_FEATURE", new FeatureAccess());

        try (MockedStatic<OrganizationUtils> orgUtils = mockStatic(OrganizationUtils.class)) {
            String id = org.getId();
            String email = org.getAdminEmail();
            /*
             * this mimics a situation when akto-usage-service is reachable,
             * as we get a feature from the service.
             * Irrespective of the plan, every user has a non-empty set of features.
             */
            orgUtils.when(() -> OrganizationUtils.fetchEntitlements(id, email))
                    .thenReturn(featureList);
            orgUtils.when(() -> OrganizationUtils.getFeatureWiseAllowed(featureList))
                    .thenReturn(featureWiseAllowed);
            // initially when org is created
            assertEquals(0, org.getLastFeatureMapUpdate());
            assertEquals(false, org.checkExpirationWithAktoSync());
            org = InitializerListener.fetchAndSaveFeatureWiseAllowed(org);
            /*
             * lastUpdateTimestamp is always updated the first time,
             * irrespective of akto-usage-service's reachability.
             */
            assertNotEquals(0, org.getLastFeatureMapUpdate());
            assertEquals(false, org.checkExpirationWithAktoSync());
            int temp = Context.now() - 16 * 60;
            org.setLastFeatureMapUpdate(temp);
            org = InitializerListener.fetchAndSaveFeatureWiseAllowed(org);
            /*
             * If akto-usage-service is reachable the
             * lastUpdateTimestamp always updated.
             */
            assertNotEquals(temp, org.getLastFeatureMapUpdate());
            assertEquals(false, org.checkExpirationWithAktoSync());

            /*
             * If Hardcoded expired
             * Note: this can only be set if akto-usage-service is reachable.
             */
            org.setExpired(true);
            assertEquals(true, org.checkExpirationWithAktoSync());

        }
    }

}
