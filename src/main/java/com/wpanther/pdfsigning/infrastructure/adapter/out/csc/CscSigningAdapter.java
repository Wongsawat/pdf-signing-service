package com.wpanther.pdfsigning.infrastructure.adapter.out.csc;

import com.wpanther.pdfsigning.domain.model.PadesLevel;
import com.wpanther.pdfsigning.domain.model.SigningException;
import com.wpanther.pdfsigning.application.port.out.SigningPort;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client.CSCApiClient;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client.CSCAuthClient;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client.SadTokenValidator;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.dto.CSCAuthorizeRequest;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.dto.CSCAuthorizeResponse;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.dto.CSCSignatureRequest;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.dto.CSCSignatureResponse;
import com.wpanther.pdfsigning.infrastructure.config.properties.CscProperties;
import com.wpanther.pdfsigning.infrastructure.adapter.out.pdf.CertificateParser;
import com.wpanther.pdfsigning.infrastructure.adapter.out.pdf.CertificateValidator;
import com.wpanther.pdfsigning.infrastructure.adapter.out.pdf.PadesCmsBuilder;
import com.wpanther.pdfsigning.infrastructure.adapter.out.pdf.PadesEmbedder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Secondary adapter for CSC-based PDF signing.
 * <p>
 * Implements {@link SigningPort} using the CSC API v2.0 for deferred signing.
 * This adapter encapsulates all CSC-specific logic including:
 * <ul>
 *   <li>Authorization to obtain SAD token</li>
 *   <li>Hash signing via signHash endpoint</li>
 *   <li>CMS/PKCS#7 signature construction</li>
 *   <li>Certificate validation</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CscSigningAdapter implements SigningPort {

    private final CSCAuthClient authClient;
    private final CSCApiClient apiClient;
    private final PadesCmsBuilder cmsBuilder;
    private final PadesEmbedder pdfEmbedder;
    private final CertificateParser certificateParser;
    private final CertificateValidator certificateValidator;
    private final SadTokenValidator sadTokenValidator;
    private final CscProperties cscProperties;

    @Override
    public SigningResult signPdfWithCertChain(byte[] pdfBytes, byte[] digest, PadesLevel padesLevel) {
        log.debug("Starting CSC signing process with PAdES level: {}", padesLevel);

        try {
            // Step 1: Authorize with CSC API to get SAD token
            log.debug("Authorizing signing operation with CSC API");
            String base64urlDigest = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            CSCAuthorizeResponse authResponse = authClient.authorize(
                CSCAuthorizeRequest.builder()
                    .clientId(cscProperties.getClientId())
                    .credentialID(cscProperties.getCredentialId())
                    .numSignatures("1")
                    .hashAlgo(cscProperties.getHashAlgo())
                    .hash(new String[]{base64urlDigest})
                    .build()
            );

            // Step 2: Validate SAD token (security critical)
            sadTokenValidator.validate(authResponse, cscProperties.getCredentialId());
            log.debug("SAD token validated successfully");

            // Step 3: Sign the hash via CSC API
            log.debug("Signing hash via CSC API");
            CSCSignatureRequest signRequest = CSCSignatureRequest.builder()
                .clientId(cscProperties.getClientId())
                .credentialID(cscProperties.getCredentialId())
                .SAD(authResponse.getSAD())
                .hashAlgo(cscProperties.getHashAlgo())
                .signatureData(CSCSignatureRequest.SignatureData.builder()
                    .hashToSign(new String[]{base64urlDigest})
                    .build())
                .build();

            CSCSignatureResponse signResponse = apiClient.signHash(signRequest);
            log.debug("Received signature from CSC API");

            // Step 4: Parse certificate chain from response
            log.debug("Parsing certificate chain");
            X509Certificate[] responseCertChain = certificateParser.parseCertificateChain(
                signResponse.getCertificate()
            );
            log.debug("Parsed certificate chain: {} certificates", responseCertChain.length);

            // Step 5: Validate certificate chain (security critical)
            log.debug("Validating certificate chain");
            certificateValidator.validateChain(responseCertChain);
            log.debug("Certificate chain validated successfully");

            // Step 6: Build CMS/PKCS#7 signature
            log.debug("Building CMS/PKCS#7 signature");
            byte[] rawSignature = Base64.getDecoder().decode(signResponse.getSignatures()[0]);
            byte[] cmsSignature = cmsBuilder.buildCmsSignature(
                rawSignature,
                responseCertChain,
                digest
            );
            log.debug("Built CMS signature: {} bytes", cmsSignature.length);

            // Step 7: Embed signature into PDF
            log.debug("Embedding signature into PDF");
            byte[] signedPdf = pdfEmbedder.embedSignature(pdfBytes, cmsSignature);
            log.debug("Embedded signature, signed PDF size: {} bytes", signedPdf.length);

            // Return both signed PDF and certificate chain
            return new SigningResult(signedPdf, responseCertChain);

        } catch (SigningException e) {
            // Re-throw domain exceptions as-is
            throw e;
        } catch (Exception e) {
            // Wrap unexpected exceptions
            log.error("Unexpected error signing PDF with CSC API", e);
            throw new SigningException("Failed to sign PDF: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateCertificateChain(X509Certificate[] certChain) {
        log.debug("Validating certificate chain with {} certificates", certChain.length);
        try {
            certificateValidator.validateChain(certChain);
            log.debug("Certificate chain validated successfully");
        } catch (SigningException e) {
            // Re-throw domain exceptions as-is
            throw e;
        } catch (Exception e) {
            // Wrap unexpected exceptions
            log.error("Unexpected error validating certificate chain", e);
            throw new SigningException("Certificate validation failed: " + e.getMessage(), e);
        }
    }
}
