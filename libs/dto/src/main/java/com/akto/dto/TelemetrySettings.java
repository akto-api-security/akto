package com.akto.dto;

public class TelemetrySettings {

    public static final String CUSTOMER_ENABLED = "customerEnabled";
    private boolean customerEnabled;

    public static final String CUSTOMER_ENABLED_AT = "customerEnabledAt";

    private int customerEnabledAt;

    public static final String STIGG_ENABLED = "stiggEnabled";

    private boolean stiggEnabled;
    public static final String STIGG_ENABLED_AT = "stiggEnabledAt";

    private int stiggEnabledAt;

    public boolean isTelemetryEnabled(){
        return customerEnabled || stiggEnabled;
    }


    public boolean getCustomerEnabled() {
        return customerEnabled;
    }

    public void setCustomerEnabled(boolean customerEnabled) {
        this.customerEnabled = customerEnabled;
    }

    public int getCustomerEnabledAt() {
        return customerEnabledAt;
    }

    public void setCustomerEnabledAt(int customerEnabledAt) {
        this.customerEnabledAt = customerEnabledAt;
    }

    public boolean getStiggEnabled() {
        return stiggEnabled;
    }

    public void setStiggEnabled(boolean stiggEnabled) {
        this.stiggEnabled = stiggEnabled;
    }

    public int getStiggEnabledAt() {
        return stiggEnabledAt;
    }

    public void setStiggEnabledAt(int stiggEnabledAt) {
        this.stiggEnabledAt = stiggEnabledAt;
    }
}
