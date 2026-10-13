package com.akto.action;

import com.akto.ca.CertificateAuthority;
import com.akto.dao.context.Context;
import com.akto.data_actor.DbLayer;
import com.akto.dto.EndpointCaRoot;
import com.akto.dto.EndpointDeviceCa;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.ActionSupport;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Certificate authority for the endpoint shield's TLS interception.
 *
 * The agent cannot read a prompt without terminating TLS, and it cannot terminate TLS
 * without a certificate the client trusts. Previously each machine self-signed its own CA
 * and the installer ran `security add-trusted-cert` locally — which fails silently in some
 * elevation contexts and can never be delivered by MDM, because a configuration profile
 * carries a fixed certificate and every machine's was different.
 *
 * This action anchors every device on one root per account. The root's public half ships
 * in an MDM profile once; each device generates its own key, sends a CSR here, and gets
 * back a short-lived intermediate constrained to the AI hosts the proxy actually routes.
 * No private key is ever transported, and nothing on the device needs to be trusted
 * individually.
 */
@Getter
@Setter
public class EndpointCaAction extends ActionSupport {

    private static final LoggerMaker loggerMaker =
            new LoggerMaker(EndpointCaAction.class, LogDb.DB_ABS);

    /**
     * Issuances allowed per device per day.
     *
     * This endpoint mints CAs that the whole tenant fleet trusts, so an uncapped caller is
     * the difference between "one laptop was compromised" and "the attacker holds as many
     * trusted CAs as they care to ask for". Normal use is one at install plus one per
     * renewal, i.e. roughly two per year — 10 leaves room for reinstall loops and genuine
     * retries while still bounding abuse to something an operator will notice in the ledger.
     */
    private static final int MAX_ISSUANCES_PER_DEVICE_PER_DAY = 10;

    private static final int ONE_DAY_SECONDS = 24 * 60 * 60;

    // --- inputs ---
    private String csr;
    private String deviceId;
    private String serial;

    // --- outputs ---
    private String intermediatePem;
    private String rootPem;
    private String rootFingerprintSha256;
    private String issuedSerial;
    private int notAfter;
    private List<String> permittedDomains;

    /**
     * Signs a device CSR as an intermediate CA.
     *
     * The CSR contributes its public key and nothing else — subject, basic constraints and
     * name constraints are all imposed by CertificateAuthority. A caller able to choose its
     * own constraints could mint an unconstrained CA, which turns a device-level compromise
     * into a fleet-level one.
     */
    public String signDeviceCa() {
        try {
            if (csr == null || csr.trim().isEmpty()) {
                addActionError("csr cannot be empty");
                return ERROR.toUpperCase();
            }
            if (deviceId == null || deviceId.trim().isEmpty()) {
                addActionError("deviceId cannot be empty");
                return ERROR.toUpperCase();
            }

            int accountId = Context.accountId.get();

            long recent = DbLayer.countEndpointDeviceCaIssuedSince(
                    deviceId, Context.now() - ONE_DAY_SECONDS);
            if (recent >= MAX_ISSUANCES_PER_DEVICE_PER_DAY) {
                // Logged at error level on purpose: a device asking for its eleventh CA in a
                // day is either stuck in a reinstall loop or being abused, and both need
                // somebody to look.
                loggerMaker.errorAndAddToDb("endpoint CA issuance quota exceeded for device "
                        + deviceId + " (account " + accountId + "), issued " + recent + " in 24h");
                addActionError("issuance quota exceeded for this device");
                return ERROR.toUpperCase();
            }

            EndpointCaRoot root = DbLayer.fetchOrCreateEndpointCaRoot(accountId);
            if (root == null) {
                addActionError("could not resolve the account root CA");
                return ERROR.toUpperCase();
            }

            CertificateAuthority.SignedDeviceCa signed = CertificateAuthority.signDeviceCa(
                    csr, deviceId, accountId, root.getCertPem(), root.getPrivateKeyPem(), null);

            EndpointDeviceCa record = new EndpointDeviceCa();
            record.setSerial(signed.serial);
            record.setAccountId(accountId);
            record.setDeviceId(deviceId);
            record.setSubject(signed.subject);
            record.setFingerprintSha256(signed.fingerprintSha256);
            record.setPermittedDomains(signed.permittedDomains);
            record.setNotBefore(signed.notBeforeEpoch);
            record.setNotAfter(signed.notAfterEpoch);
            record.setIssuedAt(Context.now());
            record.setStatus(EndpointDeviceCa.STATUS_ACTIVE);
            // Recorded before the certificate is returned: a row with no certificate in the
            // field is recoverable, a certificate in the field with no row is not auditable.
            DbLayer.insertEndpointDeviceCa(record);

            this.intermediatePem = signed.certPem;
            // The root rides along so the agent can assemble mitmproxy-ca.pem in one step.
            // mitmproxy must serve the intermediate as well as the leaf, or clients receive
            // a leaf with nothing linking it to the trusted root and validation fails.
            this.rootPem = root.getCertPem();
            this.rootFingerprintSha256 = root.getFingerprintSha256();
            this.issuedSerial = signed.serial;
            this.notAfter = signed.notAfterEpoch;
            this.permittedDomains = signed.permittedDomains;

            loggerMaker.infoAndAddToDb("issued endpoint device CA serial=" + signed.serial
                    + " device=" + deviceId + " account=" + accountId
                    + " notAfter=" + signed.notAfterEpoch);

            return SUCCESS.toUpperCase();
        } catch (IllegalArgumentException e) {
            // Malformed or unacceptable CSR — the caller's fault, not ours. The message is
            // safe to surface because it only ever describes the CSR the caller sent.
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in signDeviceCa " + e.toString());
            addActionError("could not sign the device CA");
            return ERROR.toUpperCase();
        }
    }

    /**
     * Returns the account's root certificate — public half only.
     *
     * Used to build the MDM configuration profile, and by the agent to learn the
     * fingerprint it should expect to find trusted in the System keychain.
     */
    public String fetchEndpointCaRoot() {
        try {
            int accountId = Context.accountId.get();
            EndpointCaRoot root = DbLayer.fetchOrCreateEndpointCaRoot(accountId);
            if (root == null) {
                addActionError("could not resolve the account root CA");
                return ERROR.toUpperCase();
            }
            this.rootPem = root.getCertPem();
            this.rootFingerprintSha256 = root.getFingerprintSha256();
            this.notAfter = root.getNotAfter();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in fetchEndpointCaRoot " + e.toString());
            addActionError("could not fetch the root CA");
            return ERROR.toUpperCase();
        }
    }

    /**
     * Marks a device's intermediate revoked.
     *
     * This stops renewals and records the decision; it does not retroactively invalidate
     * leaves the device already minted, because an intercepted client has no reason to
     * consult a CRL for what it believes is the real server. Short validity is the control
     * that actually expires a stolen key — revocation is the audit trail.
     */
    public String revokeEndpointDeviceCa() {
        try {
            if (serial == null || serial.trim().isEmpty()) {
                addActionError("serial cannot be empty");
                return ERROR.toUpperCase();
            }
            DbLayer.revokeEndpointDeviceCa(serial);
            loggerMaker.infoAndAddToDb("revoked endpoint device CA serial=" + serial);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in revokeEndpointDeviceCa " + e.toString());
            addActionError("could not revoke the device CA");
            return ERROR.toUpperCase();
        }
    }
}
