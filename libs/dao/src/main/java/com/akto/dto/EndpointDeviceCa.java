package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.List;

/**
 * Issuance record for one device's intermediate CA.
 *
 * This is the ledger that answers "which machines currently hold a CA we signed" — the
 * question a security review always asks and which cannot be reconstructed after the fact,
 * since the certificate itself is only ever stored on the device.
 *
 * The private key is NOT here and never reaches the server: the device generates its own
 * key and sends only a CSR. That is what keeps a stolen key scoped to one laptop.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EndpointDeviceCa {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";

    /** Certificate serial, uppercase hex. Unique per issuance, so a renewal is a new row
     *  rather than an overwrite — the history of what was issued stays intact. */
    public static final String SERIAL = "serial";
    @BsonId
    private String serial;

    public static final String ACCOUNT_ID = "accountId";
    private int accountId;

    public static final String DEVICE_ID = "deviceId";
    private String deviceId;

    public static final String SUBJECT = "subject";
    private String subject;

    /** Uppercase hex SHA-256 of the device CA's DER. */
    public static final String FINGERPRINT_SHA256 = "fingerprintSha256";
    private String fingerprintSha256;

    /** Name constraints actually encoded into the certificate. Recorded because the
     *  constraint set is policy that can change — without this you cannot tell what an
     *  already-issued certificate is permitted to do. */
    public static final String PERMITTED_DOMAINS = "permittedDomains";
    private List<String> permittedDomains;

    public static final String NOT_BEFORE = "notBefore";
    private int notBefore;

    public static final String NOT_AFTER = "notAfter";
    private int notAfter;

    public static final String ISSUED_AT = "issuedAt";
    private int issuedAt;

    public static final String STATUS = "status";
    private String status;

    /** Set when an operator revokes the certificate. Revocation stops renewals and is
     *  recorded for audit; it does not retroactively stop a leaf the device already
     *  signed, because intercepted clients do not check CRL/OCSP. Short validity is the
     *  real control — see CertificateAuthority.DEVICE_CA_VALIDITY_DAYS. */
    public static final String REVOKED_AT = "revokedAt";
    private int revokedAt;
}
