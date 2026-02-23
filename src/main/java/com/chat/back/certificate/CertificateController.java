package com.chat.back.certificate;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the CA and intermediate X.509 certificates over HTTP.
 *
 * <ul>
 *   <li>{@code GET /api/certificates/ca}           – PEM-encoded CA certificate</li>
 *   <li>{@code GET /api/certificates/intermediate} – PEM-encoded intermediate certificate</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    /**
     * Returns the root CA certificate in PEM format.
     */
    @GetMapping(value = "/ca", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getCaCertificate() {
        return ResponseEntity.ok(certificateService.getCaCertificatePem());
    }

    /**
     * Returns the intermediate CA certificate in PEM format.
     */
    @GetMapping(value = "/intermediate", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getIntermediateCertificate() {
        return ResponseEntity.ok(certificateService.getIntermediateCertificatePem());
    }
}
