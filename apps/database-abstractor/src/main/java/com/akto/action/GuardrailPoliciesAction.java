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
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching guardrail policies: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }
}
