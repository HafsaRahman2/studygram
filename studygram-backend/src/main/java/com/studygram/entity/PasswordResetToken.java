package com.studygram.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/*
 * PasswordResetToken Entity - A single-use ticket for resetting a password
 *
 * WHY THIS EXISTS
 *
 * The old design let anyone reset any account just by knowing the email
 * address. That is a complete account takeover with no proof of identity.
 *
 * The correct flow has two steps:
 *
 *   1. User asks to reset       -> we create a random token tied to their
 *                                  account and deliver it to a channel only
 *                                  they control (their email inbox).
 *   2. User submits the token   -> possession of the token IS the proof that
 *      plus a new password         they own that email address.
 *
 * Three rules make the token safe:
 *   - RANDOM   : a UUID, so it cannot be guessed
 *   - EXPIRING : useless after 30 minutes, limiting the damage if it leaks
 *   - SINGLE-USE: marked used the moment it works, so a leaked token from an
 *                 old email cannot be replayed
 */
@Entity
@Table(name = "password_reset_tokens")
@Data
@NoArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * The random secret we hand to the user. Unique so two accounts can never
     * collide, and indexed by the database because we look tokens up by it.
     */
    @Column(nullable = false, unique = true)
    private String token;

    /*
     * Which account this token can reset.
     * Nothing else about the request is trusted - not an email in the request
     * body, not a userId in the URL. The token alone decides whose password
     * changes.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /*
     * After this moment the token is dead, even if unused.
     */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /*
     * Flipped to true the first time the token successfully changes a password.
     * A used token can never be used again.
     */
    @Column(nullable = false)
    private boolean used = false;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /*
     * A token is only good if it has not been used AND has not expired.
     * Putting this check on the entity keeps the rule in one place instead of
     * being re-implemented (and eventually mis-implemented) by callers.
     */
    public boolean isValid() {
        return !used && LocalDateTime.now().isBefore(expiresAt);
    }

}
