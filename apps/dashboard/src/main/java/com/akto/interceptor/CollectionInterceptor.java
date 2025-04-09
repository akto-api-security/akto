package com.akto.interceptor;

import java.util.Set;

import com.akto.action.TrafficAction;
import com.akto.action.observe.InventoryAction;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.DashboardMode;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ActionSupport;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;

public class CollectionInterceptor extends AbstractInterceptor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageInterceptor.class, LogDb.DASHBOARD);

    boolean checkDeactivated(int apiCollectionId) {
        Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();
        return deactivatedCollections.contains(apiCollectionId);
    }

    public final static String errorMessage = "This API is not available for deactivated collections";

    @Override
    public String intercept(ActionInvocation invocation) throws Exception {
        boolean deactivated = false;

        if(!DashboardMode.isMetered()){
            return invocation.invoke();
        }

        try {
            Object actionObject = invocation.getInvocationContext().getActionInvocation().getAction();

            if (actionObject instanceof InventoryAction) {
                InventoryAction action = (InventoryAction) actionObject;
                deactivated = checkDeactivated(action.getApiCollectionId());
            } else if (actionObject instanceof TrafficAction) {
                TrafficAction action = (TrafficAction) actionObject;
                deactivated = checkDeactivated(action.getApiCollectionId());
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in processing action in collection interceptor", LogDb.DASHBOARD);
        }

        if (deactivated) {
            ((ActionSupport) invocation.getAction()).addActionError(errorMessage);
            return UsageInterceptor.UNAUTHORIZED;
        }

        return invocation.invoke();
    }

}