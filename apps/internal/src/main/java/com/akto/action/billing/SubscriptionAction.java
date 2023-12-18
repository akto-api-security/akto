package com.akto.action.internal;

import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.billing.Organization;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.stigg.StiggReporterClient;
import com.mongodb.BasicDBObject;

import static com.opensymphony.xwork2.Action.ERROR;
import static com.opensymphony.xwork2.Action.SUCCESS;

public class SubscriptionAction {

    private Organization organization;

    private static final LoggerMaker loggerMaker = new LoggerMaker(SubscriptionAction.class);
    String planId;
    String billingPeriod;
    String successUrl;
    String cancelUrl;

    BasicDBObject checkoutResult;

    public String provisionSubscription() {
        try {
            String checkoutResultStr = StiggReporterClient.instance.provisionSubscription(orgId, planId, billingPeriod, successUrl, cancelUrl);
            checkoutResult = BasicDBObject.parse(checkoutResultStr);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("customer provision subscription failed for: " + orgId, LogDb.BILLING);
            return ERROR.toUpperCase();
        }
    }

    private String orgId;
    public String fetchOrgDetails() {
        this.organization = OrganizationsDao.instance.findOne(Organization.ID, orgId);
        return SUCCESS.toUpperCase();
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public void setBillingPeriod(String billingPeriod) {
        this.billingPeriod = billingPeriod;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }

    public BasicDBObject getCheckoutResult() {
        return checkoutResult;
    }
}
