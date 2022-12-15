package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.OtpTestDataDao;
import com.akto.dto.testing.OtpTestData;
import com.google.gson.Gson;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
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
                Updates.set("otpText", otpText),
                Updates.set("usedInLoginFlow", false)
        );

        OtpTestDataDao.instance.getMCollection().findOneAndUpdate(Filters.eq("uuid", uuid), updates);

        return SUCCESS.toUpperCase();
    }

    public String fetchOtpData() {

        if (regex == null || uuid == null) {
            addActionError("regex, uuid cannot be null");
            return ERROR.toUpperCase();
        }

        Bson filters = Filters.and(
                Filters.eq("uuid", uuid),
                Filters.eq("usedInLoginFlow", true)
        );
        OtpTestData otpTestData = null;
        try {
            otpTestData = OtpTestDataDao.instance.findOne(filters);
        } catch(Exception e) {
            logger.error("Error fetching otp data for uuid " + uuid + " error " + e.getMessage());
            return ERROR.toUpperCase();
        }

        otp = extractVerificationCode(otpTestData.getOtpText(), regex);

        return SUCCESS.toUpperCase();

    }

    private String extractVerificationCode(String text, String regex) {
        System.out.println(regex);
        System.out.println(regex.replace("\\", "\\\\"));
        Pattern pattern = Pattern.compile(regex.replace("\\", "\\\\"));
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







//        for (RequestData data : authMechanism.getRequestData()) {
//            if (!(data.getType().equals("EMAIL_CODE_VERIFICATION") || data.getType().equals("MOBILE_CODE_VERIFICATION"))) {
//                continue;
//            }
//            LoginVerificationCodeData verificationCodeData = data.getVerificationCodeData();
//
//            String key = verificationCodeData.getKey();
//            String body = data.getBody();
//            String verificationCode;
//            try {
//                verificationCode = extractVerificationCode(verificationCodeBody, verificationCodeData.getRegexString());
//            } catch (Exception e) {
//                loggerMaker.errorAndAddToDb("error parsing regex string " + verificationCodeData.getRegexString() +
//                        "for auth Id " + uuid);
//                return ERROR.toUpperCase();
//            }
//
//            if (verificationCode == null) {
//                loggerMaker.errorAndAddToDb("error extracting verification code for auth Id " + uuid);
//                return ERROR.toUpperCase();
//            }
//
//            Gson gson = new Gson();
//            Map<String, Object> json = gson.fromJson(body, Map.class);
//            json.put(key, verificationCode);
//
//            JSONObject jsonBody = new JSONObject();
//            for (Map.Entry<String, Object> entry : json.entrySet()) {
//                jsonBody.put(entry.getKey(), entry.getValue());
//            }
//            data.setBody(jsonBody.toString());
//        }
//
//        AuthMechanismsDao.instance.replaceOne(filters, authMechanism);
}
