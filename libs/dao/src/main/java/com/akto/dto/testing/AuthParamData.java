package com.akto.dto.testing;

import com.akto.dto.testing.TLSAuthParam.CertificateType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthParamData {

    private AuthParam.Location where;
    private String key;

    private String value;
    private Boolean showHeader;

    private String certAuthorityCertificate;
    private CertificateType certificateType;
    private String clientCertificate;
    private String clientKey;

    public AuthParamData() { }
    public AuthParamData(AuthParam.Location where, String key, String value, Boolean showHeader) {
        this.where = where;
        this.key = key;
        this.value = value;
        this.showHeader = showHeader;
    }

    public AuthParam.Location getWhere() {return this.where;}

    public String getKey() {
        return this.key;
    }

    public String getValue() {
        return this.value;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setWhere(AuthParam.Location where) {this.where = where;}

    public Boolean validate() {
        if (this.key == null || this.value == null || this.where == null) {
            return false;
        }
        return true;
    }

    public Boolean getShowHeader() {
        return showHeader;
    }

    public void setShowHeader(Boolean showHeader) {
        this.showHeader = showHeader;
    }
}


