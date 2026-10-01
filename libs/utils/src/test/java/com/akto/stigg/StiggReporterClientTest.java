package com.akto.stigg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.akto.stigg.StiggReporterClient.PromoGrantPlan;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import org.junit.jupiter.api.Test;

public class StiggReporterClientTest {

    @Test
    public void extractFeatureLabelReadsMetadataKey() {
        BasicDBObject feature = new BasicDBObject("refId", "feature-threat-detection")
                .append("additionalMetaData", new BasicDBObject("key", " THREAT_DETECTION "));
        assertEquals("THREAT_DETECTION", StiggReporterClient.extractFeatureLabel(feature));
    }

    @Test
    public void extractFeatureLabelReturnsEmptyWhenMissing() {
        assertEquals("", StiggReporterClient.extractFeatureLabel(null));
        assertEquals("", StiggReporterClient.extractFeatureLabel(new BasicDBObject("refId", "feature-x")));
        assertEquals("", StiggReporterClient.extractFeatureLabel(
                new BasicDBObject("additionalMetaData", new BasicDBObject("other", "THREAT_DETECTION"))));
    }

    @Test
    public void findExistingPromoMatchesFeatureRefId() {
        BasicDBObject threat = promo("feature-threat-detection", "Active", "LIFETIME");
        BasicDBObject agents = promo("feature-ai-agents", "Expired", "ONE_MONTH");
        BasicDBList promos = new BasicDBList();
        promos.add(threat);
        promos.add(agents);
        BasicDBObject customer = new BasicDBObject("promotionalEntitlements", promos);

        assertEquals(threat, StiggReporterClient.findExistingPromo(customer, "feature-threat-detection"));
        assertEquals(agents, StiggReporterClient.findExistingPromo(customer, "feature-ai-agents"));
        assertNull(StiggReporterClient.findExistingPromo(customer, "feature-missing"));
        assertNull(StiggReporterClient.findExistingPromo(null, "feature-threat-detection"));
        assertNull(StiggReporterClient.findExistingPromo(new BasicDBObject(), "feature-threat-detection"));
    }

    @Test
    public void planGrantIsIdempotentForActiveLifetime() {
        assertEquals(PromoGrantPlan.ALREADY_GRANTED,
                StiggReporterClient.planGrant(promo("feature-x", "Active", "LIFETIME")));
        assertEquals(PromoGrantPlan.ALREADY_GRANTED,
                StiggReporterClient.planGrant(promo("feature-x", "ACTIVE", "lifetime")));
    }

    @Test
    public void planGrantRevokesActiveOrPausedNonLifetime() {
        assertEquals(PromoGrantPlan.REVOKE_THEN_GRANT,
                StiggReporterClient.planGrant(promo("feature-x", "Active", "ONE_MONTH")));
        assertEquals(PromoGrantPlan.REVOKE_THEN_GRANT,
                StiggReporterClient.planGrant(promo("feature-x", "Paused", "LIFETIME")));
        assertEquals(PromoGrantPlan.REVOKE_THEN_GRANT,
                StiggReporterClient.planGrant(promo("feature-x", "PAUSED", "CUSTOM")));
    }

    @Test
    public void planGrantGrantsWhenMissingOrExpired() {
        assertEquals(PromoGrantPlan.GRANT, StiggReporterClient.planGrant(null));
        assertEquals(PromoGrantPlan.GRANT,
                StiggReporterClient.planGrant(promo("feature-x", "Expired", "ONE_MONTH")));
        assertEquals(PromoGrantPlan.GRANT,
                StiggReporterClient.planGrant(promo("feature-x", "EXPIRED", "LIFETIME")));
    }

    @Test
    public void duplicatePromoErrorDetectsStiggConflict() {
        assertTrue(StiggReporterClient.isDuplicatePromoError("DuplicatedEntityNotAllowed"));
        assertTrue(StiggReporterClient.isDuplicatePromoError("Entitlement already exists for feature"));
        assertTrue(StiggReporterClient.isDuplicatePromoError("already granted"));
        assertFalse(StiggReporterClient.isDuplicatePromoError("No Stigg feature found"));
        assertFalse(StiggReporterClient.isDuplicatePromoError(null));
    }

    private static BasicDBObject promo(String featureId, String status, String period) {
        return new BasicDBObject("status", status)
                .append("period", period)
                .append("feature", new BasicDBObject("refId", featureId).append("displayName", featureId));
    }
}
