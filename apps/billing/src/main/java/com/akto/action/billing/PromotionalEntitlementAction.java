package com.akto.action.billing;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.stigg.StiggReporterClient;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.commons.lang3.StringUtils;

public class PromotionalEntitlementAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(PromotionalEntitlementAction.class);

    private String customerId;
    private String featureLabel;
    private String featureId;
    private String requestedBy;
    private String customerName;
    private String customerEmail;
    private String featureDisplayName;
    private String period = "LIFETIME";
    private BasicDBList grantedEntitlements;

    public String grantPromotionalEntitlement() {
        customerId = StringUtils.trimToEmpty(customerId);
        featureLabel = StringUtils.trimToEmpty(featureLabel);
        if (StringUtils.isEmpty(customerId) || StringUtils.isEmpty(featureLabel)) {
            addActionError("customerId and featureLabel are required");
            return Action.ERROR.toUpperCase();
        }

        try {
            BasicDBObject customer = StiggReporterClient.instance.fetchCustomer(customerId);
            customerName = customer.getString("name");
            customerEmail = customer.getString("email");

            featureId = StiggReporterClient.instance.findFeatureIdByLabel(featureLabel);
            grantedEntitlements = StiggReporterClient.instance.grantLifetimePromotionalEntitlement(customerId, featureId, customer);
            if (!grantedEntitlements.isEmpty() && grantedEntitlements.get(0) instanceof BasicDBObject) {
                BasicDBObject granted = (BasicDBObject) grantedEntitlements.get(0);
                BasicDBObject feature = (BasicDBObject) granted.get("feature");
                if (feature != null) {
                    featureDisplayName = feature.getString("displayName");
                }
            }

            loggerMaker.infoAndAddToDb(String.format(
                    "Granted lifetime promotional entitlement customerId=%s featureLabel=%s featureId=%s requestedBy=%s",
                    customerId, featureLabel, featureId, StringUtils.defaultString(requestedBy)
            ), LogDb.BILLING);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format(
                    "Failed to grant promotional entitlement customerId=%s featureLabel=%s. Error: %s",
                    customerId, featureLabel, e.getMessage()
            ), LogDb.BILLING);
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public void setFeatureLabel(String featureLabel) {
        this.featureLabel = featureLabel;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getFeatureLabel() {
        return featureLabel;
    }

    public String getFeatureId() {
        return featureId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getFeatureDisplayName() {
        return featureDisplayName;
    }

    public String getPeriod() {
        return period;
    }

    public BasicDBList getGrantedEntitlements() {
        return grantedEntitlements;
    }
}
