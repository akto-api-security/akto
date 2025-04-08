package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.OtpTestDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.testing.OtpTestData;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

public class OtpTestDataAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(OtpTestDataAction.class, LogDb.DASHBOARD);;

    private String regex;

    private String uuid;

    private String otpText;

    private String otp;


    public String saveOtpData() {

        if (otpText == null || uuid == null) {
            addActionError("otpText, uuid cannot be null");
            return ERROR.toUpperCase();
        }

        int curTime = Context.now();
        Bson updates = Updates.combine(
                Updates.set("otpText", otpText),
                Updates.set("createdAtEpoch", curTime)
        );

        OtpTestDataDao.instance.updateOne(Filters.eq("uuid", uuid), updates); 
        return SUCCESS.toUpperCase();
    }

    public String fetchOtpData() {

        if (uuid == null) {
            addActionError("uuid cannot be null");
            return ERROR.toUpperCase();
        }

        int timeFilterVal = Context.now() - 5 * 60;

        Bson filters = Filters.and(
            Filters.eq("uuid", uuid),
            Filters.gte("createdAtEpoch", timeFilterVal)
        );
        OtpTestData otpTestData = null;
        try {
            otpTestData = OtpTestDataDao.instance.findOne(filters);
            if (otpTestData == null) {
                addActionError("otp data not received for uuid " + uuid);
                return ERROR.toUpperCase();
            }
        } catch(Exception e) {
            addActionError("Error fetching otp data for uuid " + uuid + " error " + e.getMessage());
            return ERROR.toUpperCase();
        }

        otpText = otpTestData.getOtpText();

        return SUCCESS.toUpperCase();
    }

    public String getRegex() {
        return this.regex;
    }

    public String getUuid() {
        return this.uuid;
    }

    public String getOtpText() {
        return this.otpText;
    }

    public String getOtp() {
        return this.otp;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void setOtpText(String otpText) {
        this.otpText = otpText;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
}
