package com.studygram.security;

import com.studygram.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * Unit tests for JwtService.
 *
 * No Spring context and no database - JwtService has no collaborators, so it
 * can be constructed directly. These run in milliseconds, which matters because
 * a slow test suite is one people skip.
 *
 * The interesting tests here are the negative ones. That a valid token can be
 * read back is table stakes; what actually protects accounts is that a TAMPERED
 * token is refused.
 */
class JwtServiceTest {

    private JwtService jwtService;

    /* Base64 for a 32+ byte string, which is the minimum HS256 accepts. */
    private static final String TEST_SECRET =
            "dGVzdC1vbmx5LXNlY3JldC1ub3QtdXNlZC1hbnl3aGVyZS1yZWFsLTEyMzQ1Ng==";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();

        /*
         * The secret and expiry are normally injected by Spring from
         * configuration. ReflectionTestUtils sets them directly so this test
         * does not need to boot an application context just to fill in two
         * fields.
         */
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "expiryMinutes", 60L);
    }

    private User userWithId(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    @Test
    @DisplayName("a token can be created and read back")
    void roundTrip() {
        String token = jwtService.createToken(userWithId(42L, "hafsa123"));

        AuthenticatedUser result = jwtService.verifyToken(token);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.username()).isEqualTo("hafsa123");
    }

    @Test
    @DisplayName("a token has the three dot-separated parts of a JWT")
    void tokenIsWellFormed() {
        String token = jwtService.createToken(userWithId(1L, "someone"));

        // header.payload.signature
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("THE IMPORTANT ONE: a tampered token is rejected")
    void tamperedTokenIsRejected() {
        String token = jwtService.createToken(userWithId(1L, "attacker"));

        /*
         * Simulate someone editing the payload to claim they are a different
         * user. The payload is only base64 - anyone can decode it, change the
         * subject and re-encode it. What they CANNOT do is produce a matching
         * signature without the secret.
         *
         * Flipping one character of the payload is enough to prove the point:
         * the signature no longer matches what the payload hashes to.
         */
        String[] parts = token.split("\\.");
        String tamperedPayload = parts[1].substring(0, parts[1].length() - 1)
                + (parts[1].endsWith("A") ? "B" : "A");
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThat(jwtService.verifyToken(tampered))
                .as("a token whose payload was edited must not be accepted")
                .isNull();
    }

    @Test
    @DisplayName("a token signed with a different secret is rejected")
    void tokenFromAnotherSecretIsRejected() {
        /*
         * What an attacker would do if they knew the format but not the key:
         * sign their own token and hope nobody checks. This is why the secret
         * must never be committed - possession of it IS the ability to log in
         * as anybody.
         */
        JwtService imposter = new JwtService();
        ReflectionTestUtils.setField(imposter, "secret",
                "YW4tZW50aXJlbHktZGlmZmVyZW50LXNlY3JldC12YWx1ZS1oZXJlLTk4NzY1NA==");
        ReflectionTestUtils.setField(imposter, "expiryMinutes", 60L);

        String forged = imposter.createToken(userWithId(1L, "hafsa123"));

        assertThat(jwtService.verifyToken(forged)).isNull();
    }

    @Test
    @DisplayName("an expired token is rejected")
    void expiredTokenIsRejected() {
        /*
         * A negative expiry mints a token that was already dead when it was
         * created, which is the cleanest way to test expiry without making the
         * test sleep.
         */
        ReflectionTestUtils.setField(jwtService, "expiryMinutes", -10L);

        String expired = jwtService.createToken(userWithId(1L, "hafsa123"));

        assertThat(jwtService.verifyToken(expired)).isNull();
    }

    @Test
    @DisplayName("garbage input is rejected without throwing")
    void garbageIsRejected() {
        /*
         * The filter calls this on anything that arrives in an Authorization
         * header. It must never throw, or a malformed header from a bot
         * scanning the internet would become a 500 error in the logs.
         */
        assertThat(jwtService.verifyToken("not-a-token")).isNull();
        assertThat(jwtService.verifyToken("")).isNull();
        assertThat(jwtService.verifyToken("a.b.c")).isNull();
    }

    @Test
    @DisplayName("a secret shorter than 32 bytes is refused at startup")
    void shortSecretIsRefused() {
        /*
         * A short key weakens the signature to the point where it could be
         * brute-forced. Failing loudly at startup is far better than running
         * insecurely and never mentioning it.
         */
        JwtService weak = new JwtService();
        ReflectionTestUtils.setField(weak, "secret", "tooshort");
        ReflectionTestUtils.setField(weak, "expiryMinutes", 60L);

        assertThatThrownBy(() -> weak.createToken(userWithId(1L, "x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

}
