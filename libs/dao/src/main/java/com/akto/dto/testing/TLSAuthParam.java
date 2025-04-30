package com.akto.dto.testing;

import com.akto.dto.OriginalHttpRequest;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TLSAuthParam extends AuthParam {

    String certAuthorityCertificate;
    CertificateType certificateType;
    String clientCertificate;
    String clientKey;

    public enum CertificateType {
        /*
         * contains base64 encoded data
         */
        PEM,
        /*
         * DER and P12 are binary formats.
         * Certificate types are interconvertible.
         * Supporting currently for PEM only.
         */
        DER,
        P12,
        /*
         * ENG is hardware key.
         * Added here for completeness, but not used in the code.
         */
        ENG;
    }

    boolean addAuthTokens(OriginalHttpRequest request) {
        request.setTlsAuthParam(this);
        return true;
    }

    public boolean removeAuthTokens(OriginalHttpRequest request) {
        request.setTlsAuthParam(null);
        // re-check this.
        return true;
    }

    public boolean authTokenPresent(OriginalHttpRequest request) {
        // re-check this.
        return true;
    }

    public Boolean getShowHeader() {
        return false;
    }

    public Location getWhere() {
        return Location.TLS;
    }

    public String getValue() {
        return "TLS-CLIENT-CERTIFICATE";
    }

    public String getKey() {
        return "TLS-AUTH-KEY";
    }

    public void setValue(String value) {
        // no-op
    }

    @Override
    public int hashCode() {

        if (certAuthorityCertificate == null) {
            certAuthorityCertificate = "";
        }
        if (certificateType == null) {
            certificateType = CertificateType.PEM;
        }
        if (clientCertificate == null) {
            clientCertificate = "";
        }
        if (clientKey == null) {
            clientKey = "";
        }

        return certAuthorityCertificate.hashCode() + certificateType.hashCode() + clientCertificate.hashCode()
                + clientKey.hashCode();
    }

}