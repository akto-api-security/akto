package com.akto.interceptor;

import java.util.HashMap;
import java.util.Map;

import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.filter.UserDetailsFilter;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.DashboardMode;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ActionSupport;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;

public class UsageInterceptor extends AbstractInterceptor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageInterceptor.class);

    String featureLabel;

    public void setFeatureLabel(String featureLabel) {
        this.featureLabel = featureLabel;
    }

    final static String UNAUTHORIZED = "UNAUTHORIZED";

    @Override
    public String intercept(ActionInvocation invocation) throws Exception {

        try {

            if (!DashboardMode.isMetered()) {
                return invocation.invoke();
            }

            Map<String, Object> session = invocation.getInvocationContext().getSession();
            int sessionAccId = (Integer) session.get(UserDetailsFilter.ACCOUNT_ID);

            Organization organization = OrganizationsDao.instance.findOne(
                    Filters.in(Organization.ACCOUNTS, sessionAccId));

            if (organization == null) {
                throw new Exception("Organization not found");
            }

            HashMap<String, FeatureAccess> featureWiseAllowed = organization.getFeatureWiseAllowed();

            if(featureWiseAllowed == null || featureWiseAllowed.isEmpty()) {
                throw new Exception("feature map not found or empty for organization " + organization.getId());
            }

            int gracePeriod = organization.getGracePeriod();

            String[] features = featureLabel.split(" ");
            for (String feature : features) {
                feature = feature.trim();

                if(feature.isEmpty()) {
                    continue;
                }

                FeatureAccess featureAccess = featureWiseAllowed.get(feature);

                if (UsageInterceptorUtil.checkContextSpecificFeatureAccess(invocation, feature)) {

                    /*
                     * if the feature doesn't exist in the entitlements map,
                     * then the user is unauthorized to access the feature
                     */
                    if (featureAccess == null ||
                            !featureAccess.getIsGranted()) {
                        ((ActionSupport) invocation.getAction())
                                .addActionError("This feature is not available in your plan.");
                        return UNAUTHORIZED;
                    }
                    if (featureAccess.checkOverageAfterGrace(gracePeriod)) {
                        ((ActionSupport) invocation.getAction())
                                .addActionError("You have exceeded the limit of this feature.");
                        return UNAUTHORIZED;
                    }

                }
            }

        } catch (Exception e) {
            String api = invocation.getProxy().getActionName();
            String error = "Error in UsageInterceptor for api: " + api + " ERROR: " + e.getMessage();
            loggerMaker.errorAndAddToDb(error, LogDb.DASHBOARD);
        }

        return invocation.invoke();
    }

}