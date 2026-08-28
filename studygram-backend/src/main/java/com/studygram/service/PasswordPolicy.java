package com.studygram.service;

import java.util.Set;

/*
 * PasswordPolicy - What counts as a strong enough password
 *
 * One place, so signup, password change and password reset cannot drift apart
 * and end up enforcing three different rules.
 *
 * WHY THESE RULES AND NOT OTHERS
 *
 * The obvious instinct is to demand an uppercase letter, a number, a symbol,
 * and no repeated characters. Modern guidance (NIST SP 800-63B) says the
 * opposite, because those rules backfire:
 *
 *   - People satisfy them in the most predictable way possible. Demand a
 *     capital and a number and you get "Password1" - which meets every rule and
 *     is among the first things any attacker tries.
 *   - Complicated rules push people to write passwords down or reuse one
 *     "strong" password everywhere, which is worse than a simple unique one.
 *
 * What actually resists guessing is LENGTH and UNPREDICTABILITY. So this asks
 * for:
 *
 *   1. At least 8 characters - length does more work than any character rule
 *   2. Not one of the passwords everybody already uses
 *   3. Not simply the username or email repeated back
 *
 * Deliberately NOT required: uppercase, symbols, or a mandatory mix. A long
 * passphrase like "purple raccoon breakfast" is far stronger than "Xy7!q" and
 * this policy should not reject it.
 */
public class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    /*
     * A short blocklist of passwords that appear at the top of every breach
     * dump. A real system would check against a list of millions (or the Have I
     * Been Pwned range API); this covers the ones that would actually be tried
     * first, without shipping a huge file.
     */
    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password123", "passw0rd",
            "12345678", "123456789", "1234567890", "qwerty123", "qwertyuiop",
            "letmein", "welcome1", "admin123", "iloveyou", "abc12345",
            "football", "monkey123", "sunshine", "princess", "dragon123",
            "trustno1", "baseball", "superman", "starwars", "whatever"
    );

    /*
     * Throws with a message the UI can show directly, rather than returning a
     * boolean and making every caller invent its own wording.
     *
     * @param password what the user typed
     * @param username their username, so the password cannot just be that
     * @param email    their email, for the same reason
     */
    public static void validate(String password, String username, String email) {

        if (password == null || password.isBlank()) {
            throw new RuntimeException("Please choose a password");
        }

        if (password.length() < MIN_LENGTH) {
            throw new RuntimeException(
                    "Password must be at least " + MIN_LENGTH + " characters");
        }

        /*
         * Compared lowercase, because "Password123" is not meaningfully harder
         * to guess than "password123" - an attacker tries both.
         */
        if (COMMON.contains(password.toLowerCase())) {
            throw new RuntimeException(
                    "That password is one of the most commonly used ones. Please pick another.");
        }

        if (username != null && password.equalsIgnoreCase(username)) {
            throw new RuntimeException("Your password cannot be your username");
        }

        if (email != null && password.equalsIgnoreCase(email)) {
            throw new RuntimeException("Your password cannot be your email address");
        }

        /*
         * Reject a single character repeated - "aaaaaaaa" passes the length
         * check but has essentially no entropy.
         */
        if (password.chars().distinct().count() < 4) {
            throw new RuntimeException("Password needs a bit more variety in it");
        }
    }

    /*
     * A 0-4 score for the strength meter in the UI.
     *
     * Not a security control - the rules above are. This exists so someone
     * typing gets useful feedback instead of a pass/fail at the end.
     */
    public static int strength(String password) {
        if (password == null || password.isEmpty()) return 0;

        int score = 0;
        if (password.length() >= 8) score++;
        if (password.length() >= 12) score++;      // length counts twice, on purpose
        if (password.chars().distinct().count() >= 8) score++;
        if (password.matches(".*[^a-zA-Z0-9].*") || password.length() >= 16) score++;

        if (COMMON.contains(password.toLowerCase())) return 0;

        return Math.min(score, 4);
    }

}
