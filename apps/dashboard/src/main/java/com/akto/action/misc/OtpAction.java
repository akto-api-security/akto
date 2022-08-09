package com.akto.action.misc;

import com.akto.action.UserAction;
import com.akto.dao.OtpMessagesDao;
import com.akto.dao.context.Context;
import com.akto.dto.OTPMessage;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OtpAction extends UserAction {


    private String from;
    private String text;
    @Override
    public String execute() {
        Context.accountId.set(1_000_000);

        System.out.println(text);
        if (text == null || !text.contains("OTP")) {
            System.out.println("But doesn't contain the word 'OTP' ");
            return SUCCESS.toUpperCase();
        }

        System.out.println("And contains OTP");
        OTPMessage otpMessage = new OTPMessage(Context.now(), from, text, Context.now());
        OtpMessagesDao.instance.insertOne(otpMessage);
        return SUCCESS.toUpperCase();
    }

    private String otp;
    public String fetchRecentOtp() {
        Context.accountId.set(1_000_000);
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
        System.out.println("found otp: " + otp);

        return SUCCESS.toUpperCase();
    }

    public String getOtp() {
        return otp;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public void setText(String text) {
        this.text = text;
    }
}
