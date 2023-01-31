package com.akto.dto.testing;

import java.util.UUID;
public class OtpTestData {

    private String uuid;

    private String otpText;

    private int createdAtEpoch;

    public OtpTestData() { }
    public OtpTestData(String uuid, String otpText, int createdAtEpoch) {
        this.uuid = uuid;
        this.otpText = otpText;
        this.createdAtEpoch = createdAtEpoch;
    }

    public String getUuid() {
        return this.uuid;
    }

    public String getOtpText() {
        return this.otpText;
    }

    public int getCreatedAtEpoch() {
        return this.createdAtEpoch;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void setOtpText(String otpText) {
        this.otpText = otpText;
    }

    public void setCreatedAtEpoch(int createdAtEpoch) {
        this.createdAtEpoch = createdAtEpoch;
    }
}
