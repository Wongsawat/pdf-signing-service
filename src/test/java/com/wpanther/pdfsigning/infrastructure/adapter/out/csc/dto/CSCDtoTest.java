package com.wpanther.pdfsigning.infrastructure.adapter.out.csc.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CSC DTOs.
 * These tests verify JSON serialization/deserialization for CSC API communication.
 */
@DisplayName("CSC DTO Tests")
class CSCDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("CSCAuthorizeRequest")
    class CSCAuthorizeRequestTests {

        @Test
        @DisplayName("Should serialize to JSON with correct field names")
        void shouldSerializeToJson() throws Exception {
            // Given
            CSCAuthorizeRequest request = CSCAuthorizeRequest.builder()
                .clientId("test-client")
                .credentialID("credential-123")
                .numSignatures("1")
                .hashAlgo("SHA256")
                .hash(new String[]{"dGhpc2lzYXhash"})
                .description("Test authorization")
                .build();

            // When
            String json = objectMapper.writeValueAsString(request);

            // Then
            assertThat(json).contains("\"clientId\":\"test-client\"");
            assertThat(json).contains("\"credentialID\":\"credential-123\"");
            assertThat(json).contains("\"numSignatures\":\"1\"");
            assertThat(json).contains("\"hashAlgo\":\"SHA256\"");
        }

        @Test
        @DisplayName("Should deserialize from JSON")
        void shouldDeserializeFromJson() throws Exception {
            // Given
            String json = "{\"clientId\":\"client\",\"credentialID\":\"cred\",\"numSignatures\":\"2\",\"hashAlgo\":\"SHA384\"}";

            // When
            CSCAuthorizeRequest request = objectMapper.readValue(json, CSCAuthorizeRequest.class);

            // Then
            assertThat(request.getClientId()).isEqualTo("client");
            assertThat(request.getCredentialID()).isEqualTo("cred");
            assertThat(request.getNumSignatures()).isEqualTo("2");
            assertThat(request.getHashAlgo()).isEqualTo("SHA384");
        }

        @Test
        @DisplayName("Should exclude null fields from JSON")
        void shouldExcludeNullFields() throws Exception {
            // Given
            CSCAuthorizeRequest request = CSCAuthorizeRequest.builder()
                .clientId("client")
                .credentialID("cred")
                .build();

            // When
            String json = objectMapper.writeValueAsString(request);

            // Then
            assertThat(json).doesNotContain("numSignatures");
            assertThat(json).doesNotContain("hashAlgo");
            assertThat(json).doesNotContain("hash");
        }
    }

    @Nested
    @DisplayName("CSCAuthorizeResponse")
    class CSCAuthorizeResponseTests {

        @Test
        @DisplayName("Should serialize and deserialize SAD token")
        void shouldSerializeSADToken() throws Exception {
            // Given
            CSCAuthorizeResponse response = new CSCAuthorizeResponse();
            response.setSAD("test-sad-token");
            response.setExpiresIn(300L);

            // When
            String json = objectMapper.writeValueAsString(response);
            CSCAuthorizeResponse deserialized = objectMapper.readValue(json, CSCAuthorizeResponse.class);

            // Then
            assertThat(deserialized.getSAD()).isEqualTo("test-sad-token");
            assertThat(deserialized.getExpiresIn()).isEqualTo(300L);
        }

        @Test
        @DisplayName("Should handle null expiresIn")
        void shouldHandleNullExpiresIn() throws Exception {
            // Given
            CSCAuthorizeResponse response = new CSCAuthorizeResponse();
            response.setSAD("token");

            // When
            String json = objectMapper.writeValueAsString(response);

            // Then
            assertThat(json).doesNotContain("expiresIn");
        }
    }

    @Nested
    @DisplayName("CSCSignatureRequest")
    class CSCSignatureRequestTests {

        @Test
        @DisplayName("Should serialize nested SignatureData")
        void shouldSerializeNestedSignatureData() throws Exception {
            // Given
            CSCSignatureRequest.SignatureData signatureData = CSCSignatureRequest.SignatureData.builder()
                .hashToSign(new String[]{"base64urlHash"})
                .build();

            CSCSignatureRequest request = CSCSignatureRequest.builder()
                .clientId("client")
                .credentialID("cred")
                .SAD("sad-token")
                .hashAlgo("SHA256")
                .signatureData(signatureData)
                .build();

            // When
            String json = objectMapper.writeValueAsString(request);

            // Then
            assertThat(json).contains("\"signatureData\"");
            assertThat(json).contains("\"hashToSign\"");
            assertThat(json).contains("base64urlHash");
        }

        @Test
        @DisplayName("Should deserialize with nested SignatureData")
        void shouldDeserializeWithNestedSignatureData() throws Exception {
            // Given
            String json = "{\"clientId\":\"c\",\"credentialID\":\"cr\",\"SAD\":\"st\",\"hashAlgo\":\"SHA512\",\"signatureData\":{\"hashToSign\":[\"hash1\"]}}";

            // When
            CSCSignatureRequest request = objectMapper.readValue(json, CSCSignatureRequest.class);

            // Then
            assertThat(request.getClientId()).isEqualTo("c");
            assertThat(request.getSignatureData()).isNotNull();
            assertThat(request.getSignatureData().getHashToSign()).containsExactly("hash1");
        }
    }

    @Nested
    @DisplayName("CSCSignatureResponse")
    class CSCSignatureResponseTests {

        @Test
        @DisplayName("Should serialize and deserialize signature array")
        void shouldSerializeSignatureArray() throws Exception {
            // Given
            CSCSignatureResponse response = CSCSignatureResponse.builder()
                .signatureAlgorithm("1.2.840.113549.1.1.11")
                .signatures(new String[]{"c2lnbmF0dXJlZmxvYg==", "c2lnMj"})
                .certificate("-----BEGIN CERTIFICATE-----\nMIIC\n-----END CERTIFICATE-----")
                .build();

            // When
            String json = objectMapper.writeValueAsString(response);
            CSCSignatureResponse deserialized = objectMapper.readValue(json, CSCSignatureResponse.class);

            // Then
            assertThat(deserialized.getSignatures()).hasSize(2);
            assertThat(deserialized.getCertificate()).startsWith("-----BEGIN CERTIFICATE-----");
        }

        @Test
        @DisplayName("Should handle optional operationID")
        void shouldHandleOptionalOperationID() throws Exception {
            // Given
            CSCSignatureResponse response = CSCSignatureResponse.builder()
                .signatures(new String[]{"sig"})
                .operationID("async-op-123")
                .build();

            // When
            String json = objectMapper.writeValueAsString(response);

            // Then
            assertThat(json).contains("\"operationID\":\"async-op-123\"");
        }
    }
}
