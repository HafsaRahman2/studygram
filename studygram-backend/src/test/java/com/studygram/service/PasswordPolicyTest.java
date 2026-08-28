package com.studygram.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * Unit tests for the password rules.
 *
 * No Spring, no database - this is a pure function, so the tests are instant.
 *
 * The interesting cases are the ones that PASS. It is easy to write a policy
 * that rejects weak passwords; the hard part is not also rejecting strong ones.
 * A rule that turns away "purple raccoon breakfast" while waving through
 * "Passw0rd!" is worse than no rule.
 */
class PasswordPolicyTest {

    private void validate(String password) {
        PasswordPolicy.validate(password, "hafsa123", "hafsa@example.com");
    }

    /* ------------------------------------------------------ should reject */

    @Test
    @DisplayName("rejects anything under 8 characters")
    void rejectsShort() {
        assertThatThrownBy(() -> validate("abc123"))
                .hasMessageContaining("at least 8");
    }

    @Test
    @DisplayName("rejects the passwords everybody uses")
    void rejectsCommon() {
        /*
         * Only entries of 8+ characters here, because anything shorter is
         * caught by the LENGTH rule first and reports that instead - which is
         * correct, and more actionable for the user ("add two characters" beats
         * "pick something less common" when the password is "letmein").
         */
        for (String common : new String[]{"password123", "12345678", "qwerty123", "iloveyou"}) {
            assertThatThrownBy(() -> validate(common))
                    .as("should reject %s", common)
                    .hasMessageContaining("commonly used");
        }
    }

    @Test
    @DisplayName("short common passwords are still rejected, just for being short")
    void rejectsShortCommon() {
        // "letmein" is 7 characters, so length catches it before the blocklist.
        assertThatThrownBy(() -> validate("letmein")).hasMessageContaining("at least 8");
    }

    @Test
    @DisplayName("rejects common passwords whatever the capitalisation")
    void rejectsCommonIgnoringCase() {
        /*
         * "Password123" is not meaningfully harder to guess than "password123".
         * Any attacker tries both, so the check has to be case-insensitive.
         */
        assertThatThrownBy(() -> validate("Password123"))
                .hasMessageContaining("commonly used");
    }

    @Test
    @DisplayName("rejects a single character repeated")
    void rejectsRepetitive() {
        // Long enough to pass the length check, almost no entropy.
        assertThatThrownBy(() -> validate("aaaaaaaaaa"))
                .hasMessageContaining("variety");
    }

    @Test
    @DisplayName("rejects your own username or email as your password")
    void rejectsIdentity() {
        assertThatThrownBy(() -> validate("hafsa123"))
                .hasMessageContaining("username");

        assertThatThrownBy(() -> validate("hafsa@example.com"))
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("rejects empty or missing")
    void rejectsEmpty() {
        assertThatThrownBy(() -> validate("")).hasMessageContaining("choose a password");
        assertThatThrownBy(() -> validate(null)).hasMessageContaining("choose a password");
    }

    /* ------------------------------------------------------ should accept */

    @Test
    @DisplayName("THE IMPORTANT ONE: accepts a long passphrase with no symbols or digits")
    void acceptsPassphrase() {
        /*
         * This is the case a naive policy gets wrong.
         *
         * "purple raccoon breakfast" has no uppercase, no digit and no symbol,
         * so any rule demanding those would reject it - despite it being far
         * harder to guess than "Xy7!q". Length and unpredictability are what
         * matter, and the policy has to reflect that or it pushes people toward
         * short cryptic passwords they cannot remember.
         */
        assertThatCode(() -> validate("purple raccoon breakfast")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("accepts ordinary decent passwords")
    void acceptsReasonable() {
        assertThatCode(() -> validate("test-run-9142")).doesNotThrowAnyException();
        assertThatCode(() -> validate("mangoTree88")).doesNotThrowAnyException();
        assertThatCode(() -> validate("k9!vRtpQz")).doesNotThrowAnyException();
    }

    /* ------------------------------------------------------ strength score */

    @Test
    @DisplayName("the strength score rises with length and variety")
    void strengthScales() {
        assertThatCode(() -> {
            int weak = PasswordPolicy.strength("abcd1234");
            int strong = PasswordPolicy.strength("purple raccoon breakfast!");

            if (strong <= weak) {
                throw new AssertionError(
                        "a long varied passphrase should score higher than a short one");
            }
            if (PasswordPolicy.strength("password123") != 0) {
                throw new AssertionError("a known-common password must score zero");
            }
        }).doesNotThrowAnyException();
    }

}
