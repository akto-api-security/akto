package com.akto.ca;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralSubtree;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.NameConstraints;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The signing core of the endpoint CA.
 *
 * Two-tier PKI: one root per account signs a distinct intermediate CA for every device,
 * and each device's mitmproxy mints leaf certificates from its own intermediate. The
 * device generates its key locally and sends only a CSR, so no signing key is ever shared
 * between machines and none is transported over the wire.
 *
 * The reason the hierarchy exists at all: a configuration profile can only carry a fixed
 * certificate, so a per-machine self-signed CA can never be distributed by MDM. Anchoring
 * on one root per account makes the profile static while keeping the key material unique
 * per device.
 */
public class CertificateAuthority {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static final String SIG_ALG = "SHA256withRSA";
    private static final int ROOT_KEY_BITS = 4096;

    /**
     * Device CAs are deliberately short-lived. Clients do not check CRL or OCSP on traffic
     * they believe is going to the real server, so revoking an intermediate does not stop a
     * device that has already been compromised. Expiry is the control that actually works:
     * a stolen key stops being useful on its own. The agent renews inside the last 30 days.
     */
    public static final int DEVICE_CA_VALIDITY_DAYS = 365;

    public static final int ROOT_CA_VALIDITY_DAYS = 3650;

    /**
     * Hosts a device CA is permitted to sign for, encoded as X.509 name constraints.
     *
     * This is the single highest-value control in the design. Without it, a key lifted off
     * one laptop is a general interception capability — banking, mail, anything. With it,
     * macOS itself refuses any leaf outside this list, so the worst case collapses to the
     * AI hosts the proxy already routes. The PAC scopes interception to the same set, so
     * constraining here costs nothing operationally.
     *
     * RFC 5280 DNS constraints match the domain and all subdomains, so "claude.ai" covers
     * "api.claude.ai" without a separate entry.
     */
    public static final List<String> DEFAULT_PERMITTED_DOMAINS = java.util.Collections.unmodifiableList(
            java.util.Arrays.asList(
                    "claude.ai",
                    "anthropic.com",
                    "claudeusercontent.com",
                    "chatgpt.com",
                    "openai.com",
                    "oaiusercontent.com"));

    public static class GeneratedRoot {
        public final String certPem;
        public final String privateKeyPem;
        public final String fingerprintSha256;
        public final String subject;
        public final int notAfterEpoch;

        GeneratedRoot(String certPem, String privateKeyPem, String fingerprintSha256,
                      String subject, int notAfterEpoch) {
            this.certPem = certPem;
            this.privateKeyPem = privateKeyPem;
            this.fingerprintSha256 = fingerprintSha256;
            this.subject = subject;
            this.notAfterEpoch = notAfterEpoch;
        }
    }

    public static class SignedDeviceCa {
        public final String certPem;
        public final String serial;
        public final String fingerprintSha256;
        public final String subject;
        public final int notBeforeEpoch;
        public final int notAfterEpoch;
        public final List<String> permittedDomains;

        SignedDeviceCa(String certPem, String serial, String fingerprintSha256, String subject,
                       int notBeforeEpoch, int notAfterEpoch, List<String> permittedDomains) {
            this.certPem = certPem;
            this.serial = serial;
            this.fingerprintSha256 = fingerprintSha256;
            this.subject = subject;
            this.notBeforeEpoch = notBeforeEpoch;
            this.notAfterEpoch = notAfterEpoch;
            this.permittedDomains = permittedDomains;
        }
    }

    /** Generates a self-signed root. Called once per account, lazily on first device enrolment. */
    public static GeneratedRoot generateRoot(int accountId) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
        kpg.initialize(ROOT_KEY_BITS, new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();

        // The account id is in the CN so an operator looking at a Mac's keychain can tell
        // which tenant a root belongs to — several Akto roots on one machine is a real
        // situation during testing, and an opaque name makes it unresolvable.
        String subject = "CN=Akto Endpoint Shield Root CA " + accountId + ",O=Akto";
        X500Name name = new X500Name(subject);

        Date notBefore = new Date();
        Date notAfter = new Date(notBefore.getTime() + TimeUnit.DAYS.toMillis(ROOT_CA_VALIDITY_DAYS));

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, randomSerial(), notBefore, notAfter, name, kp.getPublic());

        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        JcaX509ExtensionUtils utils = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                utils.createSubjectKeyIdentifier(kp.getPublic()));

        ContentSigner signer = new JcaContentSignerBuilder(SIG_ALG)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(kp.getPrivate());
        X509Certificate cert = toCertificate(builder.build(signer));

        return new GeneratedRoot(
                toPem(cert),
                toPem(kp.getPrivate()),
                fingerprint(cert),
                subject,
                (int) (notAfter.getTime() / 1000));
    }

    /**
     * Signs a device CSR as an intermediate CA.
     *
     * Everything except the public key is imposed here. The CSR's own subject and any
     * extensions it requests are discarded: a caller able to choose its own basicConstraints
     * or name constraints could mint an unconstrained CA, which is a fleet-wide compromise
     * rather than a device-level one. Treat the CSR as a carrier for a public key and
     * nothing else.
     */
    public static SignedDeviceCa signDeviceCa(String csrPem, String deviceId, int accountId,
                                              String rootCertPem, String rootKeyPem,
                                              List<String> permittedDomains) throws Exception {
        PKCS10CertificationRequest csr = parseCsr(csrPem);
        JcaPKCS10CertificationRequest jcaCsr =
                new JcaPKCS10CertificationRequest(csr).setProvider(BouncyCastleProvider.PROVIDER_NAME);

        // A CSR whose signature does not verify proves nothing about possession of the
        // private key, which is the only thing a CSR is for.
        if (!csr.isSignatureValid(new org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(jcaCsr.getPublicKey()))) {
            throw new IllegalArgumentException("CSR signature is not valid");
        }

        PublicKey devicePublicKey = jcaCsr.getPublicKey();
        if (!(devicePublicKey instanceof java.security.interfaces.RSAPublicKey)) {
            throw new IllegalArgumentException("device CA key must be RSA");
        }
        int bits = ((java.security.interfaces.RSAPublicKey) devicePublicKey).getModulus().bitLength();
        if (bits < 2048) {
            throw new IllegalArgumentException("device CA key must be at least 2048 bits, got " + bits);
        }

        X509Certificate rootCert = parseCertificate(rootCertPem);
        PrivateKey rootKey = parsePrivateKey(rootKeyPem);

        // mitmproxy is the only consumer of this subject, but it ends up visible in a
        // client's certificate viewer when someone inspects an intercepted connection, so
        // it names the product and the device rather than being opaque.
        String subject = "CN=Akto Endpoint Shield Device CA " + deviceId + ",O=Akto,OU=" + accountId;

        Date notBefore = new Date();
        Date notAfter = new Date(notBefore.getTime() + TimeUnit.DAYS.toMillis(DEVICE_CA_VALIDITY_DAYS));
        BigInteger serial = randomSerial();

        // The issuer DN must be the root's subject DN byte-for-byte. Going via
        // getName() renders it to an RFC 2253 string and re-parsing that produces a
        // structurally different DN, so the chain silently fails to link with
        // "Path does not chain with any of the trust anchors" — the certificate still
        // verifies against the root's key, which makes it look like it worked.
        X500Name issuerName = X500Name.getInstance(rootCert.getSubjectX500Principal().getEncoded());

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuerName, serial, notBefore, notAfter, new X500Name(subject), devicePublicKey);

        // pathLen 0: this CA may sign leaf certificates and nothing else. Without it a
        // stolen device key could issue further CAs and the containment story collapses.
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        // EKU chains on Apple platforms: constraining the intermediate to serverAuth means
        // leaves beneath it cannot be used for code signing or client auth even if the
        // device key is misused.
        builder.addExtension(Extension.extendedKeyUsage, false,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));

        List<String> domains = (permittedDomains == null || permittedDomains.isEmpty())
                ? DEFAULT_PERMITTED_DOMAINS : permittedDomains;
        builder.addExtension(Extension.nameConstraints, true, buildNameConstraints(domains));

        JcaX509ExtensionUtils utils = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                utils.createSubjectKeyIdentifier(devicePublicKey));
        builder.addExtension(Extension.authorityKeyIdentifier, false,
                utils.createAuthorityKeyIdentifier(rootCert));

        ContentSigner signer = new JcaContentSignerBuilder(SIG_ALG)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(rootKey);
        X509Certificate cert = toCertificate(builder.build(signer));

        return new SignedDeviceCa(
                toPem(cert),
                serial.toString(16).toUpperCase(),
                fingerprint(cert),
                subject,
                (int) (notBefore.getTime() / 1000),
                (int) (notAfter.getTime() / 1000),
                domains);
    }

    /**
     * Permitted subtrees only. An empty excluded set means "nothing outside the permitted
     * list is allowed", which is the intent — adding explicit exclusions would be weaker,
     * not stronger, because anything not enumerated would then be permitted.
     */
    private static NameConstraints buildNameConstraints(List<String> permittedDomains) {
        List<ASN1Encodable> permitted = new ArrayList<>();
        for (String d : permittedDomains) {
            permitted.add(new GeneralSubtree(new GeneralName(GeneralName.dNSName, d)));
        }
        GeneralSubtree[] subtrees = new GeneralSubtree[permitted.size()];
        for (int i = 0; i < permitted.size(); i++) {
            subtrees[i] = (GeneralSubtree) permitted.get(i);
        }
        return new NameConstraints(subtrees, null);
    }

    private static BigInteger randomSerial() {
        // 128 bits of randomness: serials must be unique per issuer, and a counter would
        // need coordination across database-abstractor instances.
        return new BigInteger(128, new SecureRandom()).abs();
    }

    public static String fingerprint(X509Certificate cert) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(cert.getEncoded());
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static X509Certificate parseCertificate(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object o = parser.readObject();
            if (!(o instanceof X509CertificateHolder)) {
                throw new IllegalArgumentException("not a certificate PEM");
            }
            return toCertificate((X509CertificateHolder) o);
        }
    }

    public static PrivateKey parsePrivateKey(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object o = parser.readObject();
            org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter conv =
                    new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter()
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME);
            if (o instanceof org.bouncycastle.openssl.PEMKeyPair) {
                return conv.getKeyPair((org.bouncycastle.openssl.PEMKeyPair) o).getPrivate();
            }
            if (o instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
                return conv.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) o);
            }
            throw new IllegalArgumentException("not a private key PEM");
        }
    }

    private static PKCS10CertificationRequest parseCsr(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object o = parser.readObject();
            if (o instanceof PKCS10CertificationRequest) {
                return (PKCS10CertificationRequest) o;
            }
            throw new IllegalArgumentException("not a CSR PEM");
        }
    }

    private static X509Certificate toCertificate(X509CertificateHolder holder) throws Exception {
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(holder);
    }

    public static String toPem(Object o) throws Exception {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter w = new JcaPEMWriter(sw)) {
            w.writeObject(o);
        }
        return sw.toString();
    }
}
