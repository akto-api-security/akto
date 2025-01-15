package com.akto.action.testing;

import org.apache.commons.lang3.StringUtils;

import com.akto.action.UserAction;
import com.akto.dao.testing.RemediationsDao;
import com.akto.dto.testing.Remediation;
import com.akto.util.Constants;

public class TestTemplatesAction extends UserAction {

    public String execute(){
        throw new IllegalStateException("Not implemented");
    }

    String testId;
    String remediation;
    public String fetchRemediationInfo() {

        if (StringUtils.isEmpty(testId)) {
            addActionError("testId is empty");
            return ERROR.toUpperCase();
        }

        Remediation remediationObj = RemediationsDao.instance.findOne(Constants.ID, testId);

        if (remediationObj == null) {
            this.remediation = ""; 
        } else {
            this.remediation = remediationObj.getRemediationText();
        }
        
        return SUCCESS.toUpperCase();
    }

    public String getTestId() {
        return this.testId;
    }

    public void setTestId(String testId) {
        this.testId = testId;
    }

    public String getRemediation() {
        return this.remediation;
    }

    public void setRemediation(String remediation) {
        this.remediation = remediation;
    }
    
    
}
