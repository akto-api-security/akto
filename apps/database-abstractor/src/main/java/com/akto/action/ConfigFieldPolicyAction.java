package com.akto.action;

import com.akto.data_actor.DbLayer;
import com.akto.dto.config_field_policy.ConfigFieldPolicy;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.ActionSupport;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfigFieldPolicyAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ConfigFieldPolicyAction.class, LogDb.DB_ABS);

    // Shared request fields
    private String agentId;
    private String deviceId;
    private String toolName;

    // Response
    private List<ConfigFieldPolicy> configFieldPolicies;

    // -------------------------------------------------------------------------
    // Agent: fetchConfigFieldPolicies
    // Returns ACTIVE policies for toolName, filtered server-side to only those
    // that apply to the requesting deviceId (empty/null devices list = apply
    // to all devices).
    // struts.xml includeProperties limits response to: hexId, policyName,
    // toolName, fieldPath, enforcedValueJson, status
    // -------------------------------------------------------------------------

    public String fetchConfigFieldPolicies() {
        try {
            if (agentId == null || agentId.isEmpty()) {
                addActionError("agentId is required");
                return ERROR.toUpperCase();
            }
            if (deviceId == null || deviceId.isEmpty()) {
                addActionError("deviceId is required");
                return ERROR.toUpperCase();
            }
            if (toolName == null || toolName.isEmpty()) {
                addActionError("toolName is required");
                return ERROR.toUpperCase();
            }

            List<ConfigFieldPolicy> all = DbLayer.fetchActiveConfigFieldPolicies(toolName);
            List<ConfigFieldPolicy> filtered = new ArrayList<>();

            for (ConfigFieldPolicy p : all) {
                List<String> devices = p.getDevices();
                if (devices == null || devices.isEmpty() || devices.contains(deviceId)) {
                    filtered.add(p);
                }
            }

            this.configFieldPolicies = filtered;
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in fetchConfigFieldPolicies: " + e.getMessage(), LogDb.DB_ABS);
            return ERROR.toUpperCase();
        }
    }
}
