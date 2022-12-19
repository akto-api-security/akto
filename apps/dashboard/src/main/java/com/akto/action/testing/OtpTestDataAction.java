package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.OtpTestDataDao;
import com.akto.dto.testing.OtpTestData;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OtpTestDataAction extends UserAction {

    private String regex;

    private String uuid;

    private String otpText;

    private String otp;

    private static final Logger logger = LoggerFactory.getLogger(OtpTestDataAction.class);

    public String saveOtpData() {

        if (otpText == null || uuid == null) {
            addActionError("otpText, uuid cannot be null");
            return ERROR.toUpperCase();
        }

        OtpTestData otpTestData = new OtpTestData(otpText, false);

        Bson updates = Updates.combine(
                Updates.set("otpText", otpText)
        );

        OtpTestDataDao.instance.updateOne(Filters.eq("uuid", uuid), updates); 

        return SUCCESS.toUpperCase();
    }

    public String fetchOtpData() {

        if (uuid == null) {
            addActionError("uuid cannot be null");
            return ERROR.toUpperCase();
        }

        Bson filters = Filters.and(
            Filters.eq("uuid", uuid)
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

    public String extractOtpData() {

        if (regex == null || uuid == null) {
            addActionError("regex, uuid cannot be null");
            return ERROR.toUpperCase();
        }

        Bson filters = Filters.and(
            Filters.eq("uuid", uuid)
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

        otp = extractVerificationCode(otpTestData.getOtpText(), regex);

        if (otp == null) {
            addActionError("No otp found for " + uuid + " error ");
            return ERROR.toUpperCase();
        }

        // todo: add time check
        
        // otpTestData.setUsedInLoginFlow(true);

        // Bson updates = Updates.combine(
        //         Updates.set("usedInLoginFlow", true)
        // );
       // OtpTestDataDao.instance.getMCollection().findOneAndUpdate(Filters.eq("uuid", uuid), updates);

        return SUCCESS.toUpperCase();

    }

    private String extractVerificationCode(String text, String regex) {
        System.out.println(regex);

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        String verificationCode = null;
        if (matcher.find()) {
            verificationCode = matcher.group(1);
        }

        return verificationCode;
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
