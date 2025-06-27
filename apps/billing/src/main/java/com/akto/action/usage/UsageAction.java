package com.akto.action.usage;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import com.akto.util.UsageUtils;
import org.apache.struts2.interceptor.ServletRequestAware;

import com.akto.dao.context.Context;
import com.akto.dao.billing.OrganizationFlagsDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.OrganizationFlags;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.metadata.ActiveAccounts;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

public class UsageAction extends ActionSupport implements ServletRequestAware {
    private UsageMetric usageMetric;
    private HttpServletRequest request;

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageAction.class);
    private int usageLowerBound;
    private int usageUpperBound;

    private String organizationId;

    public String ingestUsage() {
        try {
            String organizationId = usageMetric.getOrganizationId();
            int accountId = usageMetric.getAccountId();
            String metricType = usageMetric.getMetricType().toString();
            loggerMaker.infoAndAddToDb(String.format("Ingesting usage metric - (%s / %d ) - %s", organizationId, accountId, metricType), LogDb.BILLING);

            String ipAddress = request.getRemoteAddr();
            usageMetric.setIpAddress(ipAddress);
            usageMetric.setSyncedWithAkto(true);
            usageMetric.setAktoSaveEpoch(Context.now());
            
            // Check if organization exists
            Organization organization = OrganizationsDao.instance.findOne(Filters.eq(Organization.ID, organizationId));
            if (organization == null) {
                loggerMaker.errorAndAddToDb(String.format("Organization %s does not exist, called from %s", organizationId, ipAddress), LogDb.BILLING);
                return Action.ERROR.toUpperCase();
            }

            // Handle active_accounts usage metric
            if (usageMetric.getMetricType() == MetricTypes.ACTIVE_ACCOUNTS) {
                try {
                    Gson gson = new Gson();
                    String metadata = usageMetric.getMetadata();
                    ActiveAccounts activeAccounts = gson.fromJson(metadata, ActiveAccounts.class);
                    Set<Integer> activeAccountsSet = activeAccounts.getActiveAccounts();
                    loggerMaker.infoAndAddToDb(String.format("ACTIVE_ACCOUNTS organization %s - %s", organizationId, activeAccountsSet), LogDb.BILLING);

                    OrganizationsDao.instance.updateOne(
                        Filters.eq(Organization.ID, organizationId),
                        Updates.set(Organization.ACCOUNTS, activeAccountsSet)
                    );

                    loggerMaker.infoAndAddToDb(String.format("Updated ACTIVE_ACCOUNTS for organization %s", organizationId), LogDb.BILLING);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, String.format("Error while updating ACTIVE_ACCOUNTS for organization %s. Error: %s", organizationId, e.getMessage()), LogDb.BILLING);
                    return Action.ERROR.toUpperCase();
                }
            }

            UsageMetricsDao.instance.insertOne(usageMetric);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format("Error while ingesting usage metric. Error: %s", e.getMessage()), LogDb.BILLING);
            return Action.ERROR.toUpperCase();
        }
    
        return SUCCESS.toUpperCase();
    }

    public String flushUsageDataForOrg(){

        if(organizationId == null || organizationId.isEmpty()){
            addActionError("Organization id not provided");
            return Action.ERROR.toUpperCase();
        }

        try {
            Organization organization = OrganizationsDao.instance.findOne(Filters.eq(Organization.ID, organizationId));
            if (organization == null) {
                String message = String.format("Organization %s does not exist", organizationId);
                addActionError(message);
                loggerMaker.errorAndAddToDb(message, LogDb.BILLING);
                return Action.ERROR.toUpperCase();
            }
            int now = Context.now();
            /*
             * since we just recorded and sent the data from dashboard,
             * we need to set the limits to check for the current epoch,
             * even though it would be a non-standard epoch.
             */
            usageLowerBound = now - UsageUtils.USAGE_UPPER_BOUND_DL/2;
            usageUpperBound = now + UsageUtils.USAGE_UPPER_BOUND_DL/2;
            loggerMaker.infoAndAddToDb(String.format("Lower Bound: %d Upper bound: %d", usageLowerBound, usageUpperBound), LogDb.BILLING);
            OrganizationFlags flags = OrganizationFlagsDao.instance.findOne(new BasicDBObject());
            executorService.schedule(new Runnable() {
                public void run() {
                    InitializerListener.aggregateAndSinkUsageData(organization, usageLowerBound, usageUpperBound, flags, true);
                    loggerMaker.infoAndAddToDb(String.format("Flushed usage data for organization %s", organizationId), LogDb.BILLING);
                }
            }, 0, TimeUnit.SECONDS);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            String commonMessage = "Error while flushing usage data for organization";
            loggerMaker.errorAndAddToDb(e, String.format( commonMessage + " %s. Error: %s", organizationId, e.getMessage()), LogDb.BILLING);
            addActionError(commonMessage);
            return Action.ERROR.toUpperCase();
        }
    }

    public void setUsageMetric(UsageMetric usageMetric) {
        this.usageMetric = usageMetric;
    }

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.request = request;
    }

    public int getUsageLowerBound() {
        return usageLowerBound;
    }

    public void setUsageLowerBound(int usageLowerBound) {
        this.usageLowerBound = usageLowerBound;
    }

    public int getUsageUpperBound() {
        return usageUpperBound;
    }

    public void setUsageUpperBound(int usageUpperBound) {
        this.usageUpperBound = usageUpperBound;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }
}
