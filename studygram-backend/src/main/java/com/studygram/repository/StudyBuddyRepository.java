package com.studygram.repository;

import com.studygram.entity.StudyBuddy;
import com.studygram.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/*
 * StudyBuddyRepository - Database operations for StudyBuddy connections
 */
public interface StudyBuddyRepository extends JpaRepository<StudyBuddy, Long> {

    /*
     * Find pending requests FOR a user (requests they received)
     * These are requests waiting for the user to accept/reject
     */
    List<StudyBuddy> findByBuddyAndStatus(User buddy, String status);

    /*
     * Find pending requests BY a user (requests they sent)
     */
    List<StudyBuddy> findByUserAndStatus(User user, String status);

    /*
     * Check if a buddy relationship already exists between two users
     * (regardless of who sent the request)
     */
    @Query("SELECT sb FROM StudyBuddy sb WHERE " +
           "(sb.user = :user1 AND sb.buddy = :user2) OR " +
           "(sb.user = :user2 AND sb.buddy = :user1)")
    Optional<StudyBuddy> findExistingConnection(
            @Param("user1") User user1,
            @Param("user2") User user2
    );

    /*
     * Get all accepted buddies for a user
     * Returns connections where status = ACCEPTED and user is involved
     */
    @Query("SELECT sb FROM StudyBuddy sb WHERE " +
           "(sb.user = :user OR sb.buddy = :user) AND sb.status = 'ACCEPTED'")
    List<StudyBuddy> findAcceptedBuddies(@Param("user") User user);

    /*
     * Count accepted buddies for a user
     */
    @Query("SELECT COUNT(sb) FROM StudyBuddy sb WHERE " +
           "(sb.user = :user OR sb.buddy = :user) AND sb.status = 'ACCEPTED'")
    int countAcceptedBuddies(@Param("user") User user);

}
