package com.studygram.security;

import com.studygram.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

/*
 * JwtService - Creates and verifies the tokens that prove who a request is from
 *
 * WHAT PROBLEM THIS SOLVES
 *
 * Before this, the API identified callers by a number in the URL:
 *
 *     DELETE /api/posts/5?userId=1
 *
 * The server checked that user 1 owned post 5 - but never checked that the
 * person sending the request WAS user 1. Changing the number to 2 was enough to
 * act as somebody else. Every ownership rule in the app rested on the client
 * being honest about its own identity.
 *
 * WHAT A JWT IS
 *
 * A JSON Web Token is three base64 chunks joined by dots:
 *
 *     header . payload . signature
 *     eyJhbGci.eyJzdWIiOiIxIiwi.dBjftJeZ4CV
 *
 * The header says which algorithm signed it. The payload holds the claims -
 * here, which user this is and when the token expires. The signature is a hash
 * of the first two parts made with a secret only this server knows.
 *
 * IMPORTANT: the payload is ENCODED, NOT ENCRYPTED. Anyone holding a token can
 * read its contents - paste one into jwt.io and you will see the user id in
 * plain text. That is fine and expected; it is why nothing secret goes in a
 * token. What the signature guarantees is that the contents were not CHANGED.
 * Edit a single character of the payload and the signature stops matching, so
 * the server rejects it. You cannot forge a token for another user without the
 * secret.
 *
 * WHY TOKENS INSTEAD OF SESSIONS
 *
 * A session means the server remembers every logged-in user in memory or a
 * shared store. A token means the server remembers nothing: everything needed
 * to identify the caller travels with the request and is verified by maths.
 * That keeps the API stateless, which is simpler here and is what lets an API
 * scale to more than one server without a shared session store.
 *
 * The trade-off is that a token cannot be un-issued. Once minted it is valid
 * until it expires, so there is no instant "log out everywhere". That is why
 * the lifetime below is limited rather than indefinite.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /*
     * The signing secret.
     *
     * Read from the environment, never hardcoded - anyone who has this string
     * can mint a valid token for any user, which is total account takeover for
     * every account at once. The development default below exists so a fresh
     * clone runs, and SecurityConfig refuses to start in production without a
     * real one being set.
     *
     * HS256 requires at least 256 bits (32 bytes) of key material.
     */
    @Value("${studygram.jwt.secret}")
    private String secret;

    /*
     * How long a token stays valid, in minutes. Default 7 days.
     *
     * A balance: short lifetimes limit the damage of a stolen token, but log
     * people out constantly. A production app solves this with a short access
     * token plus a long refresh token; this project uses a single medium-lived
     * token and says so in the README.
     */
    @Value("${studygram.jwt.expiry-minutes:10080}")
    private long expiryMinutes;

    /*
     * Build the cryptographic key from the secret string.
     *
     * Kept in a field and created once because deriving it is not free, and the
     * result never changes while the application is running.
     */
    private SecretKey cachedKey;

    private SecretKey key() {
        if (cachedKey == null) {
            /*
             * The secret is expected to be base64. If it is not - for instance
             * someone set a plain passphrase - fall back to its raw bytes so
             * the app still starts, rather than failing with a decoding error
             * that gives no hint about what is wrong.
             */
            byte[] keyBytes;
            try {
                keyBytes = Decoders.BASE64.decode(secret);
            } catch (Exception e) {
                keyBytes = secret.getBytes();
            }

            if (keyBytes.length < 32) {
                throw new IllegalStateException(
                        "studygram.jwt.secret is too short. HS256 needs at least 32 bytes. "
                        + "Generate one with: openssl rand -base64 32"
                );
            }

            cachedKey = Keys.hmacShaKeyFor(keyBytes);
        }
        return cachedKey;
    }

    /*
     * CREATE A TOKEN for a user who has just proved their identity.
     *
     * Called only after a successful login or signup - this method assumes the
     * password has already been checked.
     *
     * The "subject" is the standard JWT claim for "who this token is about". We
     * put the user id there because ids never change; a username could be
     * edited later and every existing token would suddenly point at nothing.
     * The username is included as an extra claim purely so it can be read
     * without a database lookup.
     */
    public String createToken(User user) {

        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryMinutes * 60 * 1000);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key())
                .compact();
    }

    /*
     * VERIFY A TOKEN and return who it belongs to.
     *
     * parseSignedClaims() does the security-critical work: it recomputes the
     * signature and compares, and it checks the expiry. If either fails it
     * throws, and we return null.
     *
     * Returning null rather than throwing keeps the caller (the filter) simple:
     * no valid token means no authenticated user, which is an ordinary
     * situation, not an error.
     */
    public AuthenticatedUser verifyToken(String token) {

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = Long.valueOf(claims.getSubject());
            String username = claims.get("username", String.class);

            return new AuthenticatedUser(userId, username);

        } catch (JwtException | IllegalArgumentException e) {
            /*
             * Covers a tampered signature, an expired token, and outright
             * gibberish. Logged at debug because a bad token is usually just an
             * expired session, not an attack - logging it as a warning would
             * fill the log with noise.
             */
            log.debug("Rejected token: {}", e.getMessage());
            return null;
        }
    }

}
