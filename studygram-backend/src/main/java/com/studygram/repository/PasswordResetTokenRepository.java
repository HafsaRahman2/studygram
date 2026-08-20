package com.studygram.repository;

import com.studygram.entity.PasswordResetToken;
import com.studygram.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/*
 * PasswordResetTokenRepository - Database operations for reset tokens
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /*
     * Look up a token by its random string.
     * This is the only way we identify a reset request.
     */
    Optional<PasswordResetToken> findByToken(String token);

    /*
     * Invalidate every outstanding token for a user.
     *
     * Called when a new reset is requested, so that asking for a second reset
     * link silently kills the first one. Without this, every link ever emailed
     * to you stays live until it expires.
     *
     * @Modifying tells Spring this query CHANGES data rather than reading it.
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true "
         + "WHERE t.user = :user AND t.used = false")
    void invalidateAllForUser(@Param("user") User user);

}
