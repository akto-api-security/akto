package com.akto.interceptor;

import java.util.HashMap;
import java.util.Map;

import com.akto.dao.RBACDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.User;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.filter.UserDetailsFilter;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.DashboardMode;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ActionSupport;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;

public class UsageInterceptor extends AbstractInterceptor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageInterceptor.class, LogDb.DASHBOARD);

    final static String _FEATURE_LABEL = "featureLabel";

    String featureLabel;

    public void setFeatureLabel(String featureLabel) {
        this.featureLabel = featureLabel;
    }

    public final static String UNAUTHORIZED = "UNAUTHORIZED";

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

            Object moreFeatures = invocation.getInvocationContext().get(_FEATURE_LABEL);
            String featureLabel = new String(this.featureLabel);
            if (moreFeatures != null && moreFeatures instanceof String) {
                featureLabel = featureLabel.concat(" ").concat((String) moreFeatures);
            }

            String[] features = featureLabel.split(" ");
            for (String feature : features) {
                feature = feature.trim();

                if(feature.isEmpty()) {
                    continue;
                }

                FeatureAccess featureAccess = featureWiseAllowed.getOrDefault(feature, FeatureAccess.noAccess);
                featureAccess.setGracePeriod(gracePeriod);

                if (UsageInterceptorUtil.checkContextSpecificFeatureAccess(invocation, feature)) {

                    /*
                     * if the feature doesn't exist in the entitlements map,
                     * then the user is unauthorized to access the feature
                     */
                    if (!featureAccess.getIsGranted()) {
                        ((ActionSupport) invocation.getAction())
                                .addActionError("This feature is not available in your plan.");
                        return UNAUTHORIZED;
                    }
                    if (featureAccess.checkInvalidAccess()) {
                        ((ActionSupport) invocation.getAction())
                                .addActionError("You have exceeded the limit of this feature.");
                        return UNAUTHORIZED;
                    }

                }
            }


            User user = (User) session.get(RoleAccessInterceptor.USER);
            if(user == null) {
                throw new Exception("User not found in session, returning from interceptor");
            }

            if(!RBACDao.hasAccessToFeature(user.getId(), sessionAccId, featureLabel)){
                ((ActionSupport) invocation.getAction())
                                .addActionError("This role doesn't have access to the feature: " + featureLabel);
                        return UNAUTHORIZED;
            }

        } catch (Exception e) {
            String api = invocation.getProxy().getActionName();
            String error = "Error in UsageInterceptor for api: " + api + " ERROR: " + e.getMessage();
            loggerMaker.errorAndAddToDb(e, error);
        }

        return invocation.invoke();
    }

}