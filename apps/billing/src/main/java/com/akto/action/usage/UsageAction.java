package com.akto.action.usage;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts2.interceptor.ServletRequestAware;

import com.akto.dao.context.Context;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dao.usage.UsageMetricInfoDao;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.UsageMetricInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

public class UsageAction implements ServletRequestAware {
    private UsageMetric usageMetric;
    private HttpServletRequest request;

    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageAction.class);

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

            UsageMetricsDao.instance.insertOne(usageMetric);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(String.format("Error while ingesting usage metric. Error: %s", e.getMessage()), LogDb.BILLING);
            return Action.ERROR.toUpperCase();
        }
    
        return Action.SUCCESS.toUpperCase();
    }

    public void setUsageMetric(UsageMetric usageMetric) {
        this.usageMetric = usageMetric;
    }

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.request = request;
    }

}
