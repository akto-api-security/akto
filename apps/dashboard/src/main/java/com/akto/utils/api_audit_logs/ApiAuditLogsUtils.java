package com.akto.utils.api_audit_logs;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.policies.ApiAccessTypePolicy;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ActionProxy;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class ApiAuditLogsUtils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiAuditLogsUtils.class, LogDb.DASHBOARD);

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

    /* Registers an action audit generator for a specific action.
     *
     * @param actionName The unique name of the action (e.g., API endpoint).
     * @param actionClass The class type of the action.
     * @param actionAuditGenerator A function that generates an audit description from the action instance.
     * @param <T> The type of the action.
     */
    public static <T> void register(String actionName, Class<T> actionClass, Function<T, String> actionAuditGenerator) {
        API_AUDIT_LOGS_REGISTRY.put(actionName, new ActionAuditHandler<>(actionClass, actionAuditGenerator));
    }

    // Registry mapping a unique action to a specific audit generator
    private static final Map<String, ActionAuditHandler<?>> API_AUDIT_LOGS_REGISTRY = new ConcurrentHashMap<>();
    
    public static void init() {
        InventoryAuditGenerators.addToRegistry();
    }

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
