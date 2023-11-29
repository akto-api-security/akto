package com.akto.utils.usage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.usage.UsageMetricInfoDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dto.billing.Organization;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.UsageMetricInfo;
import com.akto.mixpanel.AktoMixpanel;
import com.akto.util.EmailAccountName;
import com.akto.util.UsageUtils;
import com.google.gson.Gson;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UsageMetricUtils {

    public static void syncUsageMetricWithAkto(UsageMetric usageMetric) {
        Gson gson = new Gson();
        Map<String, UsageMetric> wrapper = new HashMap<>();
        wrapper.put("usageMetric", usageMetric);
        String json = gson.toJson(wrapper);

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
                .url(UsageUtils.getUsageServiceUrl() + "/api/ingestUsage") 
                .post(body)
                .build();

        OkHttpClient client = new OkHttpClient();
        Response response = null;
                
        try {
            response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            
            UsageMetricsDao.instance.updateOne(
                Filters.eq(UsageMetric.ID, usageMetric.getId()), 
                Updates.set(UsageMetric.SYNCED_WITH_AKTO, true)
            );

            UsageMetricInfoDao.instance.updateOne(
                Filters.and(
                    Filters.eq(UsageMetricInfo.ORGANIZATION_ID, usageMetric.getOrganizationId()),
                    Filters.eq(UsageMetricInfo.ACCOUNT_ID, usageMetric.getAccountId()),
                    Filters.eq(UsageMetricInfo.METRIC_TYPE, usageMetric.getMetricType())
                ), 
                Updates.set(UsageMetricInfo.SYNC_EPOCH, Context.now())
            );
        } catch (IOException e) {
            System.out.println("Failed to sync usage metric with Akto. Error - " +  e.getMessage());
        }
        finally {
            if (response != null) {
                response.close(); // Manually close the response body
            }
        }
    }

    public static void syncUsageMetricWithMixpanel(UsageMetric usageMetric) {
        String organizationId = usageMetric.getOrganizationId();
        Organization organization = OrganizationsDao.instance.findOne(
            Filters.and(
                Filters.eq(Organization.ID, organizationId)
            )
        );

        if (organization == null) {
            return;
        }

        String adminEmail = organization.getAdminEmail();
        String dashboardMode = usageMetric.getDashboardMode();
        String eventName = String.valueOf(usageMetric.getMetricType());
        String distinct_id = adminEmail + "_" + dashboardMode;

        EmailAccountName emailAccountName = new EmailAccountName(adminEmail);
        String accountName = emailAccountName.getAccountName();

        JSONObject props = new JSONObject();
        props.put("Email ID", adminEmail);
        props.put("Account Name", accountName);
        props.put("Organization Id", organizationId);
        props.put("Account Id", usageMetric.getAccountId());
        props.put("Metric Type", usageMetric.getMetricType());
        props.put("Dashboard Version", usageMetric.getDashboardVersion());
        props.put("Dashboard Mode", usageMetric.getDashboardMode());
        props.put("Usage", usageMetric.getUsage());
        props.put("Organization Name", organization.getName());
        props.put("Source", "Dashboard");

        System.out.println("Sending event to mixpanel: " + eventName);

        AktoMixpanel aktoMixpanel = new AktoMixpanel();
        aktoMixpanel.sendEvent(distinct_id, eventName, props);
    }

}
