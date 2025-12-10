package com.akto.utils.api_audit_logs;

import com.akto.action.ApiCollectionsAction;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.policies.ApiAccessTypePolicy;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ActionProxy;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
public class ApiAuditLogsUtils {

    private static class ActionAuditHandler<T> {
        final Class<T> actionClass;
        final Function<T, String> actionAuditGenerator;

        ActionAuditHandler(Class<T> actionClass, Function<T, String> actionAuditGenerator) {
            this.actionClass = actionClass;
            this.actionAuditGenerator = actionAuditGenerator;
        }

        String process(Object candidateActionObj) {
            if (actionClass.isInstance(candidateActionObj)) {
                // Safe cast because we checked isInstance
                return actionAuditGenerator.apply(actionClass.cast(candidateActionObj));
            }
            return null; // Type mismatch
        }
    }

    private static <T> void register(String actionName, Class<T> actionClass, Function<T, String> actionAuditGenerator) {
        API_AUDIT_LOGS_REGISTRY.put(actionName, new ActionAuditHandler<>(actionClass, actionAuditGenerator));
    }

    // Registry mapping a unique action to a specific audit generator logic block
    private static final Map<String, ActionAuditHandler<?>> API_AUDIT_LOGS_REGISTRY = new HashMap<>();

    static {
        // Register api audit definitions for actions being performed in Inventory screens
        register("api/deactivateCollections", ApiCollectionsAction.class, InventoryAuditGenerators::deactivateCollections);
        register("api/activateCollections", ApiCollectionsAction.class, InventoryAuditGenerators::activateCollections);
        register("api/deleteMultipleCollections", ApiCollectionsAction.class, InventoryAuditGenerators::deleteMultipleCollections);
        register("api/updateUserCollections", ApiCollectionsAction.class, InventoryAuditGenerators::updateUserCollections);
        register("api/toggleCollectionsOutOfTestScope", ApiCollectionsAction.class, InventoryAuditGenerators::toggleCollectionsOutOfTestScope);
        register("api/updateEnvType", ApiCollectionsAction.class, InventoryAuditGenerators::updateEnvType);
        register("api/createCollection", ApiCollectionsAction.class, InventoryAuditGenerators::createCollection);
        register("api/createCustomCollection", ApiCollectionsAction.class, InventoryAuditGenerators::createCustomCollection);
        register("api/updateCustomCollection", ApiCollectionsAction.class, InventoryAuditGenerators::updateCustomCollection);
    }

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiAuditLogsUtils.class, LogDb.DASHBOARD);

    public static String createActionAuditDescription(ActionInvocation invocation) {
        String actionAuditDescription = null;

        ActionProxy proxy = invocation.getProxy();
        Object actionObj = proxy.getAction();
        String actionName = proxy.getActionName(); 

        if (API_AUDIT_LOGS_REGISTRY.containsKey(actionName)) {
            ActionAuditHandler<?> handler = API_AUDIT_LOGS_REGISTRY.get(actionName);

            if (handler != null) {
                try {
                    actionAuditDescription = handler.process(actionObj);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, String.format("Error while creating action audit description for action: %s", actionName));
                }
            }       
        }

        return actionAuditDescription;
    }

    public static List<String> getClientIpAddresses(HttpServletRequest request) {
        List<String> headers = ApiAccessTypePolicy.CLIENT_IP_HEADERS;

        List<String> ipAddresses = new ArrayList<>();

        for (String header : headers) {
            String ips = request.getHeader(header);
            if (ips != null && !ips.isEmpty() && !"unknown".equalsIgnoreCase(ips)) {
                for (String ip : ips.split(",")) {
                    ipAddresses.add(ip.trim());
                }
                break;
            }
        }

        if (ipAddresses.isEmpty()) {
            String remoteIp = request.getRemoteAddr();
            if (remoteIp != null && !remoteIp.isEmpty()) {
                ipAddresses.add(remoteIp);
            }
        }

        return ipAddresses.isEmpty() ? Collections.singletonList("unknown") : ipAddresses;
    }
}
