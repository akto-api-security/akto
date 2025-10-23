package com.akto.action;

import com.akto.dao.GuardrailPoliciesDao;
import com.akto.dto.GuardrailPolicies;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.ActionSupport;
import java.util.List;
import lombok.Getter;

@Getter
public class GuardrailPoliciesAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(GuardrailPoliciesAction.class, LogDb.DB_ABS);

    private List<GuardrailPolicies> guardrailPolicies;

    public String fetchGuardrailPolicies() {
        try {
            this.guardrailPolicies = GuardrailPoliciesDao.instance.findAll();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching guardrail policies: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }
}
