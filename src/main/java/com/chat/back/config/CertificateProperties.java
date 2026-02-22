package com.chat.back.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized configuration for certificate paths and subject fields.
 * All path properties are optional – if omitted the application will
 * auto-generate certificates under {@code ./certs/} on startup.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.certificates")
public class CertificateProperties {

    /** Directory where generated certificates will be stored. */
    private String dir = "./certs";

    /** Path to the CA certificate PEM file (optional). */
    private String caCertPath;

    /** Path to the CA private key PEM file (optional). */
    private String caKeyPath;

    /** Path to the intermediate certificate PEM file (optional). */
    private String intermediateCertPath;

    /** Path to the intermediate private key PEM file (optional). */
    private String intermediateKeyPath;

    private SubjectConfig ca = new SubjectConfig("Chat Root CA", "Chat Corp", "US");
    private SubjectConfig intermediate = new SubjectConfig("Chat Intermediate CA", "Chat Corp", "US");

    @Data
    public static class SubjectConfig {
        private String commonName;
        private String organization;
        private String country;

        public SubjectConfig() {}

        public SubjectConfig(String commonName, String organization, String country) {
            this.commonName = commonName;
            this.organization = organization;
            this.country = country;
        }
    }
}
