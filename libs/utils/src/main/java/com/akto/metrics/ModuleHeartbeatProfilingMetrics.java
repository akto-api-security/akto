package com.akto.metrics;

import com.akto.dao.context.Context;
import com.akto.dto.billing.Organization;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.usage.OrgUtils;

import java.util.List;
import java.util.Map;

public final class ModuleHeartbeatProfilingMetrics {

    private ModuleHeartbeatProfilingMetrics() {}

    public static final String ADDITIONAL_DATA_PROFILING_KEY = "profiling";

    public static void recordGaugeUpdates(List<ModuleInfo> moduleInfoList) {
        if (moduleInfoList == null || moduleInfoList.isEmpty()) {
            return;
        }
        Integer accountIdObj = Context.accountId.get();
        if (accountIdObj == null) {
            return;
        }
        int accountId = accountIdObj;
        Organization organization = OrgUtils.getOrganizationCached(accountId);
        if (organization == null || organization.getId() == null || organization.getId().isEmpty()) {
            return;
        }
        String orgId = organization.getId();
        for (ModuleInfo heartbeat : moduleInfoList) {
            if (heartbeat == null || heartbeat.getModuleType() != ModuleInfo.ModuleType.TRAFFIC_COLLECTOR) {
                continue;
            }
            Map<String, Object> additionalData = heartbeat.getAdditionalData();
            if (additionalData == null || !additionalData.containsKey(ADDITIONAL_DATA_PROFILING_KEY)) {
                continue;
            }
            Map<String, Object> profiling = asStringObjectMap(additionalData.get(ADDITIONAL_DATA_PROFILING_KEY));
            if (profiling == null) {
                continue;
            }
            String instanceId = heartbeat.getName();
            if (instanceId == null || instanceId.isEmpty()) {
                continue;
            }
            if (profiling.containsKey("host_memory_used_mb")) {
                AllMetrics.instance.setTcHostMemoryUsedMb(instanceId, toFloat(profiling.get("host_memory_used_mb")), accountId, orgId);
            }
            if (profiling.containsKey("system_cpu_percent")) {
                AllMetrics.instance.setTcSystemCpuPercent(instanceId, toFloat(profiling.get("system_cpu_percent")), accountId, orgId);
            }
            if (profiling.containsKey("goroutines")) {
                AllMetrics.instance.setTcGoroutines(instanceId, toFloat(profiling.get("goroutines")), accountId, orgId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringObjectMap(Object o) {
        if (o instanceof Map) {
            return (Map<String, Object>) o;
        }
        return null;
    }

    private static float toFloat(Object value) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if (value instanceof String) {
            return Float.parseFloat((String) value);
        }
        return 0.0f;
    }
}
