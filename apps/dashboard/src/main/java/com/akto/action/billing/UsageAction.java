package com.akto.action.billing;

import com.akto.action.UserAction;
import com.akto.dto.billing.Organization;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.billing.UsageMetricUtils;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dto.User;
import com.akto.dto.billing.Organization;
import com.akto.dto.usage.UsageMetric;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;
import com.akto.stigg.StiggReporterClient;
import com.akto.util.DashboardMode;
import com.akto.utils.billing.OrganizationUtils;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import io.micrometer.core.instrument.util.StringUtils;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;

import java.util.Arrays;
import java.util.HashSet;
import com.akto.utils.usage.OrgUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.akto.dto.type.KeyTypes.patternToSubType;

public class UsageAction extends UserAction {

    public static final String[] freeDomains = new String[] {"gmail.com","yahoo.com","hotmail.com","aol.com","hotmail.co.uk",
            "hotmail.fr","msn.com","yahoo.fr","wanadoo.fr","orange.fr","comcast.net","yahoo.co.uk","yahoo.com.br",
            "yahoo.co.in","live.com","rediffmail.com","free.fr","gmx.de","web.de","yandex.ru","ymail.com","libero.it",
            "outlook.com","uol.com.br","bol.com.br","mail.ru","cox.net","hotmail.it","sbcglobal.net","sfr.fr","live.fr",
            "verizon.net","live.co.uk","googlemail.com","yahoo.es","ig.com.br","live.nl","bigpond.com","terra.com.br",
            "yahoo.it","neuf.fr","yahoo.de","alice.it","rocketmail.com","att.net","laposte.net","facebook.com",
            "bellsouth.net","yahoo.in","hotmail.es","charter.net","yahoo.ca","yahoo.com.au","rambler.ru","hotmail.de",
            "tiscali.it","shaw.ca","yahoo.co.jp","sky.com","earthlink.net","optonline.net","freenet.de","t-online.de",
            "aliceadsl.fr","virgilio.it","home.nl","qq.com","telenet.be","me.com","yahoo.com.ar","tiscali.co.uk",
            "yahoo.com.mx","voila.fr","gmx.net","mail.com","planet.nl","tin.it","live.it","ntlworld.com","arcor.de",
            "yahoo.co.id","frontiernet.net","hetnet.nl","live.com.au","yahoo.com.sg","zonnet.nl","club-internet.fr",
            "juno.com","optusnet.com.au","blueyonder.co.uk","bluewin.ch","skynet.be","sympatico.ca","windstream.net",
            "mac.com","centurytel.net","chello.nl","live.ca","aim.com","bigpond.net.au"};

    public static final HashSet<String> freeDomainsSet = new HashSet<>(Arrays.asList(freeDomains));
    public static final ExecutorService ex = Executors.newFixedThreadPool(1);

    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageAction.class);

    String customerId;
    String planId;
    String billingPeriod;
    String successUrl;
    String cancelUrl;

    BasicDBObject checkoutResult = new BasicDBObject();
    public String provisionSubscription() {
        if (!DashboardMode.isSaasDeployment()) {
            addActionError("Invalid API");
            return ERROR.toUpperCase();
        }

        checkoutResult = OrganizationUtils.provisionSubscription(customerId, planId, billingPeriod, successUrl, cancelUrl);

        return SUCCESS.toUpperCase();
    }

    String customerToken;
    public String getCustomerStiggDetails() {
        if (!DashboardMode.isSaasDeployment()) {
            addActionError("Invalid API");
            return ERROR.toUpperCase();
        }

        if (StringUtils.isEmpty(customerId)) {
            addActionError("Empty org id found");
            return ERROR.toUpperCase();
        }

        if (!patternToSubType.get(SingleTypeInfo.UUID).matcher(customerId).matches()) {
            addActionError("Org id is not of the form uuid");
            return ERROR.toUpperCase();
        }

        BasicDBObject orgDetailsFromBilling = OrganizationUtils.fetchOrgDetails(customerId);

        if (orgDetailsFromBilling == null) {
            addActionError("No such organization found in Akto. Please contact support@akto.io.");
            return ERROR.toUpperCase();
        }

        if ("false".equalsIgnoreCase(orgDetailsFromBilling.getString(Organization.ON_PREM))) {
            addActionError("No such org found in Akto. Please contact support@akto.io.");
            return ERROR.toUpperCase();
        }

        String orgUser = orgDetailsFromBilling.getString(Organization.ADMIN_EMAIL);
        String orgUserDomain = orgUser.split("@")[1];
        String currUser = getSUser().getLogin();

        boolean isPersonalEmail = freeDomainsSet.contains(orgUserDomain.toLowerCase());

        if (isPersonalEmail && !orgUser.equalsIgnoreCase(currUser)) {
            addActionError("Org is owned by a personal email account. Please login up with your personal email id");
            return ERROR.toUpperCase();
        }

        String currUserDomain = currUser.split("@")[1];
        if (!isPersonalEmail && !orgUserDomain.equalsIgnoreCase(currUserDomain)) {
            addActionError("Org is not owned by same business. Please contact support@akto.io.");
            return ERROR.toUpperCase();
        }

        try {
            BasicDBList entitlements = OrganizationUtils.fetchEntitlements(customerId, orgUser);
        } catch (Exception e) {
            addActionError("No such organization found. Please contact support@akto.io.");
            return ERROR.toUpperCase();
        }


        this.customerToken = OrganizationUtils.fetchSignature(customerId, orgUser);

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

    public String getCustomerToken() {
        return customerToken;
    }


}