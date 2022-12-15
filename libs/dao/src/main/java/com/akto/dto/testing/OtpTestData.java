package com.akto.dto.testing;

import java.util.UUID;
public class OtpTestData {

    private String uuid;

    private String otpText;

    private Boolean usedInLoginFlow;

    public OtpTestData() { }
    public OtpTestData(String otpText, Boolean usedInLoginFlow) {
        this.uuid = UUID.randomUUID().toString();;
        this.otpText = otpText;
        this.usedInLoginFlow = usedInLoginFlow;
    }

    public String getUuid() {
        return this.uuid;
    }

    public String getOtpText() {
        return this.otpText;
    }

    public Boolean getUsedInLoginFlow() {
        return this.usedInLoginFlow;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void setOtpText(String otpText) {
        this.otpText = otpText;
    }

    public void setUsedInLoginFlow(Boolean usedInLoginFlow) {
        this.usedInLoginFlow = usedInLoginFlow;
    }
}
