package com.wpanther.pdfsigning.infrastructure.client.csc.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for CSC API DTOs.
 * Tests builder pattern, getters/setters, and JSON serialization.
 */
@DisplayName("CSC DTO Tests")
class CSCDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("CSCAuthorizeRequest Tests")
    class CSCAuthorizeRequestTests {

        @Test
        @DisplayName("Should build request with all fields")
        void shouldBuildWithAllFields() {
            // When
            CSCAuthorizeRequest request = CSCAuthorizeRequest.builder()
                .clientId("test-client")
                .credentialID("cred-123")
                .numSignatures("1")
                .hashAlgo("SHA256")
                .hash(new String[]{"abc123"})
                .description("Test signing")
                .build();

            // Then
            assertThat(request.getClientId()).isEqualTo("test-client");
            assertThat(request.getCredentialID()).isEqualTo("cred-123");
            assertThat(request.getNumSignatures()).isEqualTo("1");
            assertThat(request.getHashAlgo()).isEqualTo("SHA256");
            assertThat(request.getHash()).isEqualTo(new String[]{"abc123"});
            assertThat(request.getDescription()).isEqualTo("Test signing");
        }

        @Test
        @DisplayName("Should build request with required fields only")
        void shouldBuildWithRequiredFields() {
            // When
            CSCAuthorizeRequest request = CSCAuthorizeRequest.builder()
                .clientId("test-client")
                .credentialID("cred-123")
                .numSignatures("1")
                .hashAlgo("SHA256")
                .hash(new String[]{"abc123"})
                .build();

            // Then
            assertThat(request.getClientId()).isEqualTo("test-client");
            assertThat(request.getCredentialID()).isEqualTo("cred-123");
            assertThat(request.getNumSignatures()).isEqualTo("1");
            assertThat(request.getHashAlgo()).isEqualTo("SHA256");
            assertThat(request.getHash()).isEqualTo(new String[]{"abc123"});
            assertThat(request.getDescription()).isNull();
        }

        @Test
        @DisplayName("Should support setters")
        void shouldSupportSetters() {
            // Given
            CSCAuthorizeRequest request = new CSCAuthorizeRequest();

            // When
            request.setClientId("new-client");
            request.setCredentialID("new-cred");
            request.setNumSignatures("2");
            request.setHashAlgo("SHA384");
            request.setHash(new String[]{"xyz789"});
            request.setDescription("Updated");

            // Then
            assertThat(request.getClientId()).isEqualTo("new-client");
            assertThat(request.getCredentialID()).isEqualTo("new-cred");
            assertThat(request.getNumSignatures()).isEqualTo("2");
            assertThat(request.getHashAlgo()).isEqualTo("SHA384");
            assertThat(request.getHash()).isEqualTo(new String[]{"xyz789"});
            assertThat(request.getDescription()).isEqualTo("Updated");
        }

        @Test
        @DisplayName("Should serialize to JSON correctly")
        void shouldSerializeToJson() throws JsonProcessingException {
            // Given
            CSCAuthorizeRequest request = CSCAuthorizeRequest.builder()
                .clientId("test-client")
                .credentialID("cred-123")
                .numSignatures("1")
                .hashAlgo("SHA256")
                .hash(new String[]{"abc123"})
                .build();

            // When
            String json = objectMapper.writeValueAsString(request);

            // Then
            assertThat(json).contains("\"clientId\":\"test-client\"");
            assertThat(json).contains("\"credentialID\":\"cred-123\"");
            assertThat(json).contains("\"numSignatures\":\"1\"");
            assertThat(json).contains("\"hashAlgo\":\"SHA256\"");
            assertThat(json).contains("\"hash\":[\"abc123\"]");
        }

        @Test
        @DisplayName("Should deserialize from JSON correctly")
        void shouldDeserializeFromJson() throws JsonProcessingException {
            // Given
            String json = """
                {
                    "clientId": "test-client",
                    "credentialID": "cred-123",
                    "numSignatures": "1",
                    "hashAlgo": "SHA256",
                    "hash": ["abc123"],
                    "description": "Test"
                }
                """;

            // When
            CSCAuthorizeRequest request = objectMapper.readValue(json, CSCAuthorizeRequest.class);

            // Then
            assertThat(request.getClientId()).isEqualTo("test-client");
            assertThat(request.getCredentialID()).isEqualTo("cred-123");
            assertThat(request.getNumSignatures()).isEqualTo("1");
            assertThat(request.getHashAlgo()).isEqualTo("SHA256");
            assertThat(request.getHash()).isEqualTo(new String[]{"abc123"});
            assertThat(request.getDescription()).isEqualTo("Test");
        }

        @Test
        @DisplayName("Should handle no-args constructor")
        void shouldHandleNoArgsConstructor() {
            // When
            CSCAuthorizeRequest request = new CSCAuthorizeRequest();

            // Then
            assertThat(request).isNotNull();
            assertThat(request.getClientId()).isNull();
            assertThat(request.getCredentialID()).isNull();
        }

        @Test
        @DisplayName("Should handle all-args constructor")
        void shouldHandleAllArgsConstructor() {
            // When
            CSCAuthorizeRequest request = new CSCAuthorizeRequest(
                "test-client", "cred-123", "1", "SHA256",
                new String[]{"abc123"}, "Test"
            );

            // Then
            assertThat(request.getClientId()).isEqualTo("test-client");
            assertThat(request.getCredentialID()).isEqualTo("cred-123");
            assertThat(request.getNumSignatures()).isEqualTo("1");
            assertThat(request.getHashAlgo()).isEqualTo("SHA256");
            assertThat(request.getHash()).isEqualTo(new String[]{"abc123"});
            assertThat(request.getDescription()).isEqualTo("Test");
        }
    }

    @Nested
    @DisplayName("CSCAuthorizeResponse Tests")
    class CSCAuthorizeResponseTests {

        @Test
        @DisplayName("Should create with SAD token")
        void shouldCreateWithSadToken() {
            // Given
            CSCAuthorizeResponse response = new CSCAuthorizeResponse();
            response.setSAD("test-sad-token");

            // Then
            assertThat(response.getSAD()).isEqualTo("test-sad-token");
        }

        @Test
        @DisplayName("Should create with expiration time")
        void shouldCreateWithExpiration() {
            // Given
            CSCAuthorizeResponse response = new CSCAuthorizeResponse();
            response.setExpiresIn(300L);

            // Then
            assertThat(response.getExpiresIn()).isEqualTo(300L);
        }

        @Test
        @DisplayName("Should create with all fields using constructor")
        void shouldCreateWithAllFields() {
            // When
            CSCAuthorizeResponse response = new CSCAuthorizeResponse("sad-token", 600L);

            // Then
            assertThat(response.getSAD()).isEqualTo("sad-token");
            assertThat(response.getExpiresIn()).isEqualTo(600L);
        }

        @Test
        @DisplayName("Should handle null expiration")
        void shouldHandleNullExpiration() {
            // Given
            CSCAuthorizeResponse response = new CSCAuthorizeResponse();
            response.setSAD("token");

            // Then
            assertThat(response.getSAD()).isEqualTo("token");
            assertThat(response.getExpiresIn()).isNull();
        }

        @Test
        @DisplayName("Should serialize to JSON")
        void shouldSerializeToJson() throws JsonProcessingException {
            // Given
            CSCAuthorizeResponse response = new CSCAuthorizeResponse("sad-token", 300L);

            // When
            String json = objectMapper.writeValueAsString(response);

            // Then
            assertThat(json).contains("\"SAD\":\"sad-token\"");
            assertThat(json).contains("\"expiresIn\":300");
        }

        @Test
        @DisplayName("Should deserialize from JSON")
        void shouldDeserializeFromJson() throws JsonProcessingException {
            // Given
            String json = """
                {
                    "SAD": "test-sad-token",
                    "expiresIn": 300
                }
                """;

            // When
            CSCAuthorizeResponse response = objectMapper.readValue(json, CSCAuthorizeResponse.class);

            // Then
            assertThat(response.getSAD()).isEqualTo("test-sad-token");
            assertThat(response.getExpiresIn()).isEqualTo(300L);
        }
    }

    @Nested
    @DisplayName("CSCSignatureRequest Tests")
    class CSCSignatureRequestTests {

        @Test
        @DisplayName("Should build request with all fields")
        void shouldBuildWithAllFields() {
            // Given
            CSCSignatureRequest.SignatureData sigData = CSCSignatureRequest.SignatureData.builder()
                .hashToSign(new String[]{"abc123"})
                .build();

            // When
            CSCSignatureRequest request = CSCSignatureRequest.builder()
                .clientId("test-client")
                .credentialID("cred-123")
                .SAD("sad-token")
                .hashAlgo("SHA256")
                .signatureData(sigData)
                .async(false)
                .build();

            // Then
            assertThat(request.getClientId()).isEqualTo("test-client");
            assertThat(request.getCredentialID()).isEqualTo("cred-123");
            assertThat(request.getSAD()).isEqualTo("sad-token");
            assertThat(request.getHashAlgo()).isEqualTo("SHA256");
            assertThat(request.getSignatureData()).isEqualTo(sigData);
            assertThat(request.getAsync()).isFalse();
        }

        @Test
        @DisplayName("Should build with required fields only")
        void shouldBuildWithRequiredFields() {
            // Given
            CSCSignatureRequest.SignatureData sigData = CSCSignatureRequest.SignatureData.builder()
                .hashToSign(new String[]{"abc123"})
                .build();

            // When
            CSCSignatureRequest request = CSCSignatureRequest.builder()
                .clientId("test-client")
                .credentialID("cred-123")
                .hashAlgo("SHA256")
                .signatureData(sigData)
                .build();

            // Then
            assertThat(request.getClientId()).isEqualTo("test-client");
            assertThat(request.getCredentialID()).isEqualTo("cred-123");
            assertThat(request.getHashAlgo()).isEqualTo("SHA256");
            assertThat(request.getSignatureData()).isNotNull();
            assertThat(request.getSAD()).isNull();
            assertThat(request.getAsync()).isNull();
        }

        @Test
        @DisplayName("Should support setters")
        void shouldSupportSetters() {
            // Given
            CSCSignatureRequest request = new CSCSignatureRequest();
            CSCSignatureRequest.SignatureData sigData = new CSCSignatureRequest.SignatureData();

            // When
            request.setClientId("new-client");
            request.setCredentialID("new-cred");
            request.setSAD("new-sad");
            request.setHashAlgo("SHA384");
            request.setSignatureData(sigData);
            request.setAsync(true);

            // Then
            assertThat(request.getClientId()).isEqualTo("new-client");
            assertThat(request.getCredentialID()).isEqualTo("new-cred");
            assertThat(request.getSAD()).isEqualTo("new-sad");
            assertThat(request.getHashAlgo()).isEqualTo("SHA384");
            assertThat(request.getSignatureData()).isEqualTo(sigData);
            assertThat(request.getAsync()).isTrue();
        }

        @Test
        @DisplayName("Should serialize to JSON correctly")
        void shouldSerializeToJson() throws JsonProcessingException {
            // Given
            CSCSignatureRequest.SignatureData sigData = CSCSignatureRequest.SignatureData.builder()
                .hashToSign(new String[]{"abc123"})
                .build();

            CSCSignatureRequest request = CSCSignatureRequest.builder()
                .clientId("test-client")
                .credentialID("cred-123")
                .SAD("sad-token")
                .hashAlgo("SHA256")
                .signatureData(sigData)
                .build();

            // When
            String json = objectMapper.writeValueAsString(request);

            // Then
            assertThat(json).contains("\"clientId\":\"test-client\"");
            assertThat(json).contains("\"credentialID\":\"cred-123\"");
            assertThat(json).contains("\"SAD\":\"sad-token\"");
            assertThat(json).contains("\"hashAlgo\":\"SHA256\"");
            assertThat(json).contains("\"hashToSign\":[\"abc123\"]");
        }

        @Test
        @DisplayName("Should deserialize from JSON correctly")
        void shouldDeserializeFromJson() throws JsonProcessingException {
            // Given
            String json = """
                {
                    "clientId": "test-client",
                    "credentialID": "cred-123",
                    "SAD": "sad-token",
                    "hashAlgo": "SHA256",
                    "signatureData": {
                        "hashToSign": ["abc123"]
                    }
                }
                """;

            // When
            CSCSignatureRequest request = objectMapper.readValue(json, CSCSignatureRequest.class);

            // Then
            assertThat(request.getClientId()).isEqualTo("test-client");
            assertThat(request.getCredentialID()).isEqualTo("cred-123");
            assertThat(request.getSAD()).isEqualTo("sad-token");
            assertThat(request.getHashAlgo()).isEqualTo("SHA256");
            assertThat(request.getSignatureData()).isNotNull();
            assertThat(request.getSignatureData().getHashToSign()).isEqualTo(new String[]{"abc123"});
        }

        @Test
        @DisplayName("Should handle no-args constructor")
        void shouldHandleNoArgsConstructor() {
            // When
            CSCSignatureRequest request = new CSCSignatureRequest();

            // Then
            assertThat(request).isNotNull();
            assertThat(request.getClientId()).isNull();
        }

        @Test
        @DisplayName("Should handle all-args constructor")
        void shouldHandleAllArgsConstructor() {
            // Given
            CSCSignatureRequest.SignatureData sigData = new CSCSignatureRequest.SignatureData();
            sigData.setHashToSign(new String[]{"abc123"});

            // When
            CSCSignatureRequest request = new CSCSignatureRequest(
                "test-client", "cred-123", "sad-token", "SHA256",
                sigData, false
            );

            // Then
            assertThat(request.getClientId()).isEqualTo("test-client");
            assertThat(request.getCredentialID()).isEqualTo("cred-123");
            assertThat(request.getSAD()).isEqualTo("sad-token");
            assertThat(request.getHashAlgo()).isEqualTo("SHA256");
            assertThat(request.getSignatureData()).isEqualTo(sigData);
            assertThat(request.getAsync()).isFalse();
        }
    }

    @Nested
    @DisplayName("CSCSignatureRequest.SignatureData Tests")
    class SignatureDataTests {

        @Test
        @DisplayName("Should build with hashToSign")
        void shouldBuildWithHashToSign() {
            // When
            CSCSignatureRequest.SignatureData sigData = CSCSignatureRequest.SignatureData.builder()
                .hashToSign(new String[]{"abc123", "def456"})
                .build();

            // Then
            assertThat(sigData.getHashToSign()).isEqualTo(new String[]{"abc123", "def456"});
        }

        @Test
        @DisplayName("Should build with signatureAttributes")
        void shouldBuildWithSignatureAttributes() {
            // When
            Object attrs = new Object();
            CSCSignatureRequest.SignatureData sigData = CSCSignatureRequest.SignatureData.builder()
                .hashToSign(new String[]{"abc123"})
                .signatureAttributes(attrs)
                .build();

            // Then
            assertThat(sigData.getHashToSign()).isEqualTo(new String[]{"abc123"});
            assertThat(sigData.getSignatureAttributes()).isEqualTo(attrs);
        }

        @Test
        @DisplayName("Should support setters")
        void shouldSupportSetters() {
            // Given
            CSCSignatureRequest.SignatureData sigData = new CSCSignatureRequest.SignatureData();

            // When
            sigData.setHashToSign(new String[]{"xyz789"});
            sigData.setSignatureAttributes("attributes");

            // Then
            assertThat(sigData.getHashToSign()).isEqualTo(new String[]{"xyz789"});
            assertThat(sigData.getSignatureAttributes()).isEqualTo("attributes");
        }

        @Test
        @DisplayName("Should handle empty hash array")
        void shouldHandleEmptyHashArray() {
            // When
            CSCSignatureRequest.SignatureData sigData = CSCSignatureRequest.SignatureData.builder()
                .hashToSign(new String[]{})
                .build();

            // Then
            assertThat(sigData.getHashToSign()).isEmpty();
        }
    }

    @Nested
    @DisplayName("CSCSignatureResponse Tests")
    class CSCSignatureResponseTests {

        @Test
        @DisplayName("Should build with all fields")
        void shouldBuildWithAllFields() {
            // When
            CSCSignatureResponse response = CSCSignatureResponse.builder()
                .signatureAlgorithm("1.2.840.113549.1.1.11")
                .signatures(new String[]{"abc123"})
                .certificate("-----BEGIN CERTIFICATE-----\nMIIC...\n-----END CERTIFICATE-----")
                .operationID("op-123")
                .build();

            // Then
            assertThat(response.getSignatureAlgorithm()).isEqualTo("1.2.840.113549.1.1.11");
            assertThat(response.getSignatures()).isEqualTo(new String[]{"abc123"});
            assertThat(response.getCertificate()).contains("BEGIN CERTIFICATE");
            assertThat(response.getOperationID()).isEqualTo("op-123");
        }

        @Test
        @DisplayName("Should build with required fields only")
        void shouldBuildWithRequiredFields() {
            // When
            CSCSignatureResponse response = CSCSignatureResponse.builder()
                .signatures(new String[]{"abc123"})
                .certificate("-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----")
                .build();

            // Then
            assertThat(response.getSignatures()).isEqualTo(new String[]{"abc123"});
            assertThat(response.getCertificate()).contains("test");
            assertThat(response.getSignatureAlgorithm()).isNull();
            assertThat(response.getOperationID()).isNull();
        }

        @Test
        @DisplayName("Should support setters")
        void shouldSupportSetters() {
            // Given
            CSCSignatureResponse response = new CSCSignatureResponse();

            // When
            response.setSignatureAlgorithm("RSA");
            response.setSignatures(new String[]{"sig1"});
            response.setCertificate("cert");
            response.setOperationID("op-456");
            response.setTimestampData(null);

            // Then
            assertThat(response.getSignatureAlgorithm()).isEqualTo("RSA");
            assertThat(response.getSignatures()).isEqualTo(new String[]{"sig1"});
            assertThat(response.getCertificate()).isEqualTo("cert");
            assertThat(response.getOperationID()).isEqualTo("op-456");
        }

        @Test
        @DisplayName("Should serialize to JSON")
        void shouldSerializeToJson() throws JsonProcessingException {
            // Given
            CSCSignatureResponse response = CSCSignatureResponse.builder()
                .signatureAlgorithm("RSA")
                .signatures(new String[]{"abc123"})
                .certificate("cert-pem")
                .operationID("op-123")
                .build();

            // When
            String json = objectMapper.writeValueAsString(response);

            // Then
            assertThat(json).contains("\"signatureAlgorithm\":\"RSA\"");
            assertThat(json).contains("\"signatures\":[\"abc123\"]");
            assertThat(json).contains("\"certificate\":\"cert-pem\"");
            assertThat(json).contains("\"operationID\":\"op-123\"");
        }

        @Test
        @DisplayName("Should deserialize from JSON")
        void shouldDeserializeFromJson() throws JsonProcessingException {
            // Given
            String json = """
                {
                    "signatureAlgorithm": "1.2.840.113549.1.1.11",
                    "signatures": ["abc123"],
                    "certificate": "-----BEGIN CERTIFICATE-----\\ntest\\n-----END CERTIFICATE-----",
                    "operationID": "op-123"
                }
                """;

            // When
            CSCSignatureResponse response = objectMapper.readValue(json, CSCSignatureResponse.class);

            // Then
            assertThat(response.getSignatureAlgorithm()).isEqualTo("1.2.840.113549.1.1.11");
            assertThat(response.getSignatures()).isEqualTo(new String[]{"abc123"});
            assertThat(response.getCertificate()).contains("test");
            assertThat(response.getOperationID()).isEqualTo("op-123");
        }

        @Test
        @DisplayName("Should handle no-args constructor")
        void shouldHandleNoArgsConstructor() {
            // When
            CSCSignatureResponse response = new CSCSignatureResponse();

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getSignatures()).isNull();
        }

        @Test
        @DisplayName("Should handle all-args constructor")
        void shouldHandleAllArgsConstructor() {
            // When
            CSCSignatureResponse response = new CSCSignatureResponse(
                "RSA",
                new String[]{"sig1"},
                "cert-pem",
                "op-123",
                null
            );

            // Then
            assertThat(response.getSignatureAlgorithm()).isEqualTo("RSA");
            assertThat(response.getSignatures()).isEqualTo(new String[]{"sig1"});
            assertThat(response.getCertificate()).isEqualTo("cert-pem");
            assertThat(response.getOperationID()).isEqualTo("op-123");
            assertThat(response.getTimestampData()).isNull();
        }

        @Test
        @DisplayName("Should handle multiple signatures")
        void shouldHandleMultipleSignatures() {
            // Given
            CSCSignatureResponse response = CSCSignatureResponse.builder()
                .signatures(new String[]{"sig1", "sig2", "sig3"})
                .build();

            // Then
            assertThat(response.getSignatures()).hasSize(3);
            assertThat(response.getSignatures()).isEqualTo(new String[]{"sig1", "sig2", "sig3"});
        }
    }
}
