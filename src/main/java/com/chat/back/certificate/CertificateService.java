package com.chat.back.certificate;

import com.chat.back.config.CertificateProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Manages the CA and intermediate X.509 certificates.
 * <p>
 * On application startup ({@link PostConstruct}) the service checks whether
 * certificate files already exist at the configured (or default) paths.
 * If any file is missing, a full chain (CA + intermediate) is generated and
 * persisted to disk so subsequent restarts reuse the same certificates.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateService {

    private static final String SIGNATURE_ALGORITHM = "SHA256WithRSA";
    private static final int KEY_SIZE = 4096;
    private static final int CA_VALIDITY_DAYS = 3650;       // 10 years
    private static final int INTERMEDIATE_VALIDITY_DAYS = 1825; // 5 years

    private final CertificateProperties props;

    // In-memory holders (loaded or generated)
    private X509Certificate caCertificate;
    private PrivateKey caPrivateKey;
    private X509Certificate intermediateCertificate;
    private PrivateKey intermediatePrivateKey;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @PostConstruct
    public void init() throws Exception {
        String caCertPath        = resolve(props.getCaCertPath(),            props.getDir(), "ca.crt");
        String caKeyPath         = resolve(props.getCaKeyPath(),             props.getDir(), "ca.key");
        String intCertPath       = resolve(props.getIntermediateCertPath(),  props.getDir(), "intermediate.crt");
        String intKeyPath        = resolve(props.getIntermediateKeyPath(),   props.getDir(), "intermediate.key");

        if (allFilesExist(caCertPath, caKeyPath, intCertPath, intKeyPath)) {
            log.info("Certificate files found – loading from disk.");
            caCertificate          = loadCertificate(caCertPath);
            caPrivateKey           = loadPrivateKey(caKeyPath);
            intermediateCertificate = loadCertificate(intCertPath);
            intermediatePrivateKey  = loadPrivateKey(intKeyPath);
        } else {
            log.info("Certificate files not found – generating new CA and intermediate certificates.");
            generateAndPersist(caCertPath, caKeyPath, intCertPath, intKeyPath);
        }

        log.info("CA certificate subject      : {}", caCertificate.getSubjectX500Principal().getName());
        log.info("Intermediate cert subject   : {}", intermediateCertificate.getSubjectX500Principal().getName());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public accessors
    // ──────────────────────────────────────────────────────────────────────────

    public String getCaCertificatePem() {
        return toPem(caCertificate);
    }

    public String getIntermediateCertificatePem() {
        return toPem(intermediateCertificate);
    }

    public X509Certificate getCaCertificate() {
        return caCertificate;
    }

    public X509Certificate getIntermediateCertificate() {
        return intermediateCertificate;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Generation
    // ──────────────────────────────────────────────────────────────────────────

    private void generateAndPersist(String caCertPath, String caKeyPath,
                                     String intCertPath, String intKeyPath) throws Exception {
        Files.createDirectories(Paths.get(props.getDir()));

        // 1. Generate CA key pair & self-signed certificate
        KeyPair caKeyPair = generateKeyPair();
        CertificateProperties.SubjectConfig caSubject = props.getCa();
        X500Name caName = buildX500Name(caSubject);
        caCertificate = buildCaCertificate(caKeyPair, caName);
        caPrivateKey  = caKeyPair.getPrivate();

        // 2. Generate intermediate key pair & certificate signed by CA
        KeyPair intKeyPair = generateKeyPair();
        CertificateProperties.SubjectConfig intSubject = props.getIntermediate();
        X500Name intName = buildX500Name(intSubject);
        intermediateCertificate = buildIntermediateCertificate(intKeyPair, intName, caCertificate, caPrivateKey);
        intermediatePrivateKey  = intKeyPair.getPrivate();

        // 3. Write PEM files
        writePem(caCertificate,         caCertPath);
        writePem(caPrivateKey,           caKeyPath);
        writePem(intermediateCertificate, intCertPath);
        writePem(intermediatePrivateKey,  intKeyPath);

        log.info("Generated and saved certificates to directory: {}", props.getDir());
    }

    private KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
        kpg.initialize(KEY_SIZE, new SecureRandom());
        return kpg.generateKeyPair();
    }

    private X509Certificate buildCaCertificate(KeyPair keyPair, X500Name subject) throws Exception {
        Date notBefore = new Date();
        Date notAfter  = new Date(notBefore.getTime() + TimeUnit.DAYS.toMillis(CA_VALIDITY_DAYS));
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, keyPair.getPublic());

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                extUtils.createSubjectKeyIdentifier(keyPair.getPublic()));

        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());

        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
    }

    private X509Certificate buildIntermediateCertificate(KeyPair intKeyPair, X500Name intName,
                                                           X509Certificate caCert,
                                                           PrivateKey caKey) throws Exception {
        Date notBefore = new Date();
        Date notAfter  = new Date(notBefore.getTime() + TimeUnit.DAYS.toMillis(INTERMEDIATE_VALIDITY_DAYS));
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis() + 1);

        X500Name caName = X500Name.getInstance(caCert.getSubjectX500Principal().getEncoded());

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                caName, serial, notBefore, notAfter, intName, intKeyPair.getPublic());

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        // Intermediate CA can sign leaf certs (path length 0)
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                extUtils.createSubjectKeyIdentifier(intKeyPair.getPublic()));
        builder.addExtension(Extension.authorityKeyIdentifier, false,
                extUtils.createAuthorityKeyIdentifier(caCert));

        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(caKey);

        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // I/O helpers
    // ──────────────────────────────────────────────────────────────────────────

    private static X509Certificate loadCertificate(String path) throws Exception {
        try (PEMParser parser = new PEMParser(
                Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8))) {
            Object obj = parser.readObject();
            return new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate((X509CertificateHolder) obj);
        }
    }

    private static PrivateKey loadPrivateKey(String path) throws Exception {
        try (PEMParser parser = new PEMParser(
                Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8))) {
            Object obj = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME);
            if (obj instanceof org.bouncycastle.openssl.PEMKeyPair pair) {
                return converter.getKeyPair(pair).getPrivate();
            } else if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo info) {
                return converter.getPrivateKey(info);
            }
            throw new IllegalArgumentException("Unsupported key type in PEM file: " + obj.getClass().getName());
        }
    }

    private static void writePem(Object obj, String path) throws IOException {
        try (JcaPEMWriter writer = new JcaPEMWriter(
                Files.newBufferedWriter(Paths.get(path), StandardCharsets.UTF_8))) {
            writer.writeObject(obj);
        }
    }

    private static String toPem(Object obj) {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
            writer.writeObject(obj);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to convert object to PEM", e);
        }
        return sw.toString();
    }

    private static X500Name buildX500Name(CertificateProperties.SubjectConfig s) {
        return new X500Name(String.format("CN=%s, O=%s, C=%s",
                s.getCommonName(), s.getOrganization(), s.getCountry()));
    }

    private static String resolve(String configured, String dir, String fileName) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return dir + File.separator + fileName;
    }

    private static boolean allFilesExist(String... paths) {
        for (String p : paths) {
            if (!Files.exists(Paths.get(p))) {
                return false;
            }
        }
        return true;
    }
}
