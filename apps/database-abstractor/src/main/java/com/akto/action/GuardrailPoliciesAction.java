package com.akto.action;

import com.akto.data_actor.DbLayer;
import com.akto.dto.GuardrailPolicies;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.opensymphony.xwork2.ActionSupport;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GuardrailPoliciesAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(GuardrailPoliciesAction.class, LogDb.DB_ABS);

    private List<GuardrailPolicies> guardrailPolicies;
    private Integer updatedAfter;
    private CONTEXT_SOURCE contextSource;

    public String fetchGuardrailPolicies() {
        try {
            this.guardrailPolicies = DbLayer.fetchGuardrailPolicies(updatedAfter, contextSource);


            // Resolve targetTeams/targetRoles → device IDs fresh on every fetch.
            // Empty applyToDeviceIds = no targeting → apply to all devices.
            // Non-empty = apply only to listed device labels.
            for (GuardrailPolicies p : this.guardrailPolicies) {
                boolean hasTargeting = (p.getTargetTeams() != null && !p.getTargetTeams().isEmpty())
                        || (p.getTargetRoles() != null && !p.getTargetRoles().isEmpty());
                if (hasTargeting) {
                    try {
                        p.setApplyToDeviceIds(DbLayer.findDeviceIdsByTeamsAndRoles(
                                p.getTargetTeams(), p.getTargetRoles()));
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Error resolving device IDs for policy " + p.getHexId() + ": " + e.getMessage(), LogDb.DASHBOARD);
                        p.setApplyToDeviceIds(new java.util.ArrayList<>());
                    }
                }
            }

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching guardrail policies: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }
}
