package com.wpanther.pdfsigning.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SignedPdfDocumentId}.
 */
@DisplayName("SignedPdfDocumentId Tests")
class SignedPdfDocumentIdTest {

    @Nested
    @DisplayName("generate() method")
    class GenerateMethod {

        @Test
        @DisplayName("Should generate non-null ID with random UUID")
        void shouldGenerateNonNullId() {
            // When
            SignedPdfDocumentId id = SignedPdfDocumentId.generate();

            // Then
            assertThat(id).isNotNull();
            assertThat(id.getValue()).isNotNull();
        }

        @Test
        @DisplayName("Should generate unique IDs")
        void shouldGenerateUniqueIds() {
            // When
            SignedPdfDocumentId id1 = SignedPdfDocumentId.generate();
            SignedPdfDocumentId id2 = SignedPdfDocumentId.generate();

            // Then
            assertThat(id1).isNotEqualTo(id2);
        }
    }

    @Nested
    @DisplayName("of(UUID) method")
    class OfUuidMethod {

        @Test
        @DisplayName("Should create ID from existing UUID")
        void shouldCreateFromUuid() {
            // Given
            UUID uuid = UUID.randomUUID();

            // When
            SignedPdfDocumentId id = SignedPdfDocumentId.of(uuid);

            // Then
            assertThat(id.getValue()).isEqualTo(uuid);
        }

        @Test
        @DisplayName("Should throw exception for null UUID")
        void shouldThrowOnNullUuid() {
            // When/Then
            assertThatThrownBy(() -> SignedPdfDocumentId.of((UUID) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
        }
    }

    @Nested
    @DisplayName("of(String) method")
    class OfStringMethod {

        @Test
        @DisplayName("Should create ID from valid UUID string")
        void shouldCreateFromValidString() {
            // Given
            String uuidString = "123e4567-e89b-12d3-a456-426614174000";

            // When
            SignedPdfDocumentId id = SignedPdfDocumentId.of(uuidString);

            // Then
            assertThat(id.getValue().toString()).isEqualTo(uuidString);
        }

        @Test
        @DisplayName("Should throw exception for null string")
        void shouldThrowOnNullString() {
            // When/Then
            assertThatThrownBy(() -> SignedPdfDocumentId.of((String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw exception for empty string")
        void shouldThrowOnEmptyString() {
            // When/Then
            assertThatThrownBy(() -> SignedPdfDocumentId.of(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw exception for blank string")
        void shouldThrowOnBlankString() {
            // When/Then
            assertThatThrownBy(() -> SignedPdfDocumentId.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw exception for invalid UUID format")
        void shouldThrowOnInvalidFormat() {
            // When/Then
            assertThatThrownBy(() -> SignedPdfDocumentId.of("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid UUID format");
        }
    }

    @Nested
    @DisplayName("asString() method")
    class AsStringMethod {

        @Test
        @DisplayName("Should return UUID string representation")
        void shouldReturnString() {
            // Given
            UUID uuid = UUID.randomUUID();
            SignedPdfDocumentId id = SignedPdfDocumentId.of(uuid);

            // When
            String result = id.asString();

            // Then
            assertThat(result).isEqualTo(uuid.toString());
        }
    }

    @Nested
    @DisplayName("equals() and hashCode() methods")
    class EqualsHashCodeMethod {

        @Test
        @DisplayName("Should be equal when UUIDs are same")
        void shouldBeEqualWhenSameUuid() {
            // Given
            UUID uuid = UUID.randomUUID();
            SignedPdfDocumentId id1 = SignedPdfDocumentId.of(uuid);
            SignedPdfDocumentId id2 = SignedPdfDocumentId.of(uuid);

            // Then
            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when UUIDs are different")
        void shouldNotBeEqualWhenDifferentUuids() {
            // Given
            SignedPdfDocumentId id1 = SignedPdfDocumentId.generate();
            SignedPdfDocumentId id2 = SignedPdfDocumentId.generate();

            // Then
            assertThat(id1).isNotEqualTo(id2);
        }
    }

    @Nested
    @DisplayName("toString() method")
    class ToStringMethod {

        @Test
        @DisplayName("Should return string representation with UUID")
        void shouldReturnStringWithUuid() {
            // Given
            SignedPdfDocumentId id = SignedPdfDocumentId.generate();

            // When
            String result = id.toString();

            // Then
            assertThat(result).contains("SignedPdfDocumentId");
            assertThat(result).contains(id.getValue().toString());
        }
    }
}
