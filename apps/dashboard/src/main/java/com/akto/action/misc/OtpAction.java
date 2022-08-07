package com.akto.action.misc;

import com.akto.DaoInit;
import com.akto.action.UserAction;
import com.akto.dao.OtpMessagesDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dto.OTPMessage;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.WorkflowTestingEndpoints;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OtpAction extends UserAction {


    public static void main(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
        Context.accountId.set(1_000_000);
        TestingRun testingRun = TestingRunDao.instance.findOne(new BasicDBObject());
        WorkflowTestingEndpoints w = (WorkflowTestingEndpoints) testingRun.getTestingEndpoints();
        System.out.println(w.getWorkflowTest().getMapNodeIdToWorkflowNodeDetails().get("x6").getType());
    }

    private String otp;
    public String fetchRecentOtp() {
        List<OTPMessage> OTPMessageList = OtpMessagesDao.instance.findAll(Filters.gte("timestamp", Context.now() - 90));
        if (OTPMessageList.isEmpty()) return Action.ERROR.toUpperCase();

        OTPMessage OTPMessage = OTPMessageList.get(OTPMessageList.size()-1); // latest

        Pattern pattern = Pattern.compile("(\\d{6})");
        Matcher matcher = pattern.matcher(OTPMessage.getMessage());
        String val = null;
        if (matcher.find()) {
            val = matcher.group(0);
        }
        if (val == null || val.isEmpty()) return ERROR.toUpperCase();

        otp = val;

        return SUCCESS.toUpperCase();
    }

    public String getOtp() {
        return otp;
    }
}
