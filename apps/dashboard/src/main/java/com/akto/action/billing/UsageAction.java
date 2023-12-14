package com.akto.action.billing;

import com.akto.action.UserAction;
import com.akto.billing.UsageMetricUtils;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dto.User;
import com.akto.dto.billing.Organization;
import com.akto.dto.usage.UsageMetric;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;
import com.akto.stigg.StiggReporterClient;
import com.akto.utils.usage.OrgUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UsageAction extends UserAction {

    public static final ExecutorService ex = Executors.newFixedThreadPool(1);

    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageAction.class);

    String customerId;
    String planId;
    String billingPeriod;
    String successUrl;
    String cancelUrl;

    BasicDBObject checkoutResult = new BasicDBObject();
    public String provisionSubscription() {
        String ret = StiggReporterClient.instance.provisionSubscription(customerId, planId, billingPeriod, successUrl, cancelUrl);

        checkoutResult = BasicDBObject.parse(ret);

        return SUCCESS.toUpperCase();
    }

    public String calcUsage() {
        InitializerListener.calcUsage();

        return SUCCESS.toUpperCase();
    }
    public String syncWithAkto() {
        InitializerListener.syncWithAkto();

        return SUCCESS.toUpperCase();
    }

    public String refreshUsageData(){
        User sUser = getSUser();
        int anyAccountId = Integer.parseInt(sUser.findAnyAccountId());
        OrgUtils.getSiblingAccounts(anyAccountId).forEach(account -> {
            loggerMaker.infoAndAddToDb("Calculating usage for account: " + account.getId(), LoggerMaker.LogDb.BILLING);
            InitializerListener.calculateUsageForAccount(account);
        });

        Organization organization = OrganizationsDao.instance.findOne(
                Filters.in(Organization.ACCOUNTS, anyAccountId)
        );

        List<UsageMetric> usageMetrics = UsageMetricsDao.instance.findAll(
                Filters.eq(UsageMetric.SYNCED_WITH_AKTO, false),
                Filters.eq(UsageMetric.ORGANIZATION_ID, organization.getId())
        );

        for (UsageMetric usageMetric : usageMetrics) {
            loggerMaker.infoAndAddToDb("Syncing usage metric with billing and mixpanel: " + usageMetric.getId(), LoggerMaker.LogDb.BILLING);
            UsageMetricUtils.syncUsageMetricWithAkto(usageMetric);
            UsageMetricUtils.syncUsageMetricWithMixpanel(usageMetric);
        }

        usageMetrics = UsageMetricsDao.instance.findAll(
                Filters.eq(UsageMetric.SYNCED_WITH_AKTO, false),
                Filters.eq(UsageMetric.ORGANIZATION_ID, organization.getId())
        );

        if (usageMetrics.isEmpty()){
            loggerMaker.errorAndAddToDb("Failed to sync usage metrics", LoggerMaker.LogDb.BILLING);
            return ERROR.toUpperCase();
        }

        try{
            loggerMaker.infoAndAddToDb("Flushing usage pipeline", LoggerMaker.LogDb.BILLING);
            UsageMetricUtils.flushUsagePipelineForOrg(organization.getId());
        } catch (Exception e){
            loggerMaker.errorAndAddToDb("Failed to flush usage pipeline", LoggerMaker.LogDb.BILLING);
            return ERROR.toUpperCase();
        }
        loggerMaker.infoAndAddToDb("Usage pipeline flushed", LoggerMaker.LogDb.BILLING);
        return SUCCESS.toUpperCase();
    }

    @Override
    public String execute() {
        throw new UnsupportedOperationException();
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
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