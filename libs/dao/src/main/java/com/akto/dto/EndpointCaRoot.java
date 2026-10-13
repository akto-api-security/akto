package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;

/**
 * The per-account root CA that signs every device's intermediate CA.
 *
 * One root per account, NOT one shared root for all of Akto: a shared root would be
 * trusted on every customer's fleet at once, so a single compromise would be an
 * industry-wide incident and no customer could rotate independently. Per-account keeps
 * the blast radius to one tenant and is what makes "bring your own root" possible later
 * (store a customer-supplied cert/key here instead of a generated one).
 *
 * Only the public half ever leaves this collection — it is shipped to endpoints inside an
 * MDM configuration profile (com.apple.security.root) so macOS trusts it without any
 * `security add-trusted-cert` call on the device.
 *
 * SECURITY: privateKeyPem is the highest-value secret in the product — it can mint a CA
 * trusted by the whole tenant fleet. It lives here so the first implementation has no
 * external dependency, but the intended end state is a KMS/HSM where the key never
 * materialises in the application. CertificateAuthority signs through a java.security
 * PrivateKey, so swapping in a KMS-backed signer is a change to how this field is
 * resolved, not to the signing code.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EndpointCaRoot {

    /** Account id — one root per account. */
    public static final String ACCOUNT_ID = "accountId";
    @BsonId
    private int accountId;

    public static final String CERT_PEM = "certPem";
    private String certPem;

    public static final String PRIVATE_KEY_PEM = "privateKeyPem";
    private String privateKeyPem;

    /** Uppercase hex SHA-256 of the certificate DER — what the agent pins and what
     *  `security find-certificate -Z` prints, so the two compare directly. */
    public static final String FINGERPRINT_SHA256 = "fingerprintSha256";
    private String fingerprintSha256;

    public static final String SUBJECT = "subject";
    private String subject;

    public static final String CREATED_AT = "createdAt";
    private int createdAt;

    public static final String NOT_AFTER = "notAfter";
    private int notAfter;
}
