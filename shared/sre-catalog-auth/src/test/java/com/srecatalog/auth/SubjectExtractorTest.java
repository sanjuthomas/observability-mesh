package com.srecatalog.auth;

import com.srecatalog.common.model.Subject;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectExtractorTest {

    private final SubjectExtractor extractor = new SubjectExtractor(
            new com.fasterxml.jackson.databind.ObjectMapper());

    @Test
    void parsesOnBehalfOfTokenClaims() {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""
                {"sub":"mo-100","subject_user_id":"mo-100","title":"Analyst",
                "roles":["INSTRUCTION_CREATOR"],"groups":["MIDDLE_OFFICE"],
                "given_name":"Sarah","family_name":"Chen","supervisor_id":"mo-050"}
                """.getBytes());
        Subject subject = extractor.fromOnBehalfOfToken("header." + payload + ".sig");
        assertThat(subject.userId()).isEqualTo("mo-100");
        assertThat(subject.roles()).containsExactly("INSTRUCTION_CREATOR");
        assertThat(subject.displayName()).isEqualTo("Chen, Sarah (mo-100)");
    }

    @Test
    void parsesJwtClaimsIncludingJsonListAttributes() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("mo-100")
                .claim("preferred_username", "mo-100")
                .claim("title", "Analyst")
                .claim("roles", "[\"INSTRUCTION_CREATOR\"]")
                .claim("groups", "[\"MIDDLE_OFFICE\"]")
                .claim("given_name", "Sarah")
                .claim("family_name", "Chen")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        Subject subject = extractor.fromJwt(jwt);

        assertThat(subject.userId()).isEqualTo("mo-100");
        assertThat(subject.roles()).containsExactly("INSTRUCTION_CREATOR");
        assertThat(subject.groups()).containsExactly("MIDDLE_OFFICE");
    }

    @Test
    void parsesJwtListAndStringClaimVariants() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("pay-101")
                .claim("subject_user_id", "pay-101")
                .claim("title", "")
                .claim("roles", List.of("PAYMENT_CREATOR"))
                .claim("groups", "UP_TO_100_MILLION_CLUB")
                .claim("covering_lobs", "FICC")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        Subject subject = extractor.fromJwt(jwt);

        assertThat(subject.title()).isEqualTo("Unknown");
        assertThat(subject.roles()).containsExactly("PAYMENT_CREATOR");
        assertThat(subject.groups()).containsExactly("UP_TO_100_MILLION_CLUB");
        assertThat(subject.coveringLobs()).containsExactly("FICC");
    }

    @Test
    void rejectsInvalidOnBehalfOfToken() {
        assertThatThrownBy(() -> extractor.fromOnBehalfOfToken("not-a-jwt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesOnBehalfOfTokenWithListClaims() {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""
                {"sub":"pay-101","title":"Analyst","roles":["PAYMENT_CREATOR"],
                "groups":["UP_TO_100_MILLION_CLUB"],"covering_lobs":["FICC"]}
                """.getBytes());
        Subject subject = extractor.fromOnBehalfOfToken("header." + payload + ".sig");
        assertThat(subject.roles()).containsExactly("PAYMENT_CREATOR");
        assertThat(subject.coveringLobs()).containsExactly("FICC");
    }

    @Test
    void parsesOnBehalfOfTokenWithStringEncodedJsonClaims() {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""
                {"sub":"mo-100","title":"Analyst","roles":"[\\"INSTRUCTION_CREATOR\\"]",
                "groups":"[\\"MIDDLE_OFFICE\\"]","covering_lobs":"[]"}
                """.getBytes());
        Subject subject = extractor.fromOnBehalfOfToken("header." + payload + ".sig");
        assertThat(subject.roles()).containsExactly("INSTRUCTION_CREATOR");
        assertThat(subject.groups()).containsExactly("MIDDLE_OFFICE");
    }

    @Test
    void fromJwtUsesSubjectUserIdClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("ignored")
                .claim("subject_user_id", "mo-100")
                .claim("title", "Analyst")
                .claim("roles", List.of("INSTRUCTION_CREATOR"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        assertThat(extractor.fromJwt(jwt).userId()).isEqualTo("mo-100");
    }

    @Test
    void fromOnBehalfOfTokenHandlesInvalidJsonArrayClaim() {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""
                {"sub":"mo-100","title":"Analyst","roles":"[bad-json"}
                """.getBytes());
        Subject subject = extractor.fromOnBehalfOfToken("header." + payload + ".sig");
        assertThat(subject.roles()).containsExactly("[bad-json");
    }

    @Test
    void fromOnBehalfOfTokenUsesUnknownWhenClaimsMissing() {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""
                {"title":"Analyst","roles":[]}
                """.getBytes());
        Subject subject = extractor.fromOnBehalfOfToken("header." + payload + ".sig");
        assertThat(subject.userId()).isEqualTo("unknown");
    }

    @Test
    void fromJwtHandlesInvalidRolesJsonArray() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("mo-100")
                .claim("title", "Analyst")
                .claim("roles", "[bad")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        assertThat(extractor.fromJwt(jwt).roles()).containsExactly("[bad");
    }
}
