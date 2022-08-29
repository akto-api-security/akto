package com.akto.dao;

import com.akto.dto.OTPMessage;

public class OtpMessagesDao extends AccountsContextDao<OTPMessage>{

    public static final OtpMessagesDao instance = new OtpMessagesDao();
    @Override
    public String getCollName() {
        return "otp_messages";
    }

    @Override
    public Class<OTPMessage> getClassT() {
        return OTPMessage.class;
    }
}
