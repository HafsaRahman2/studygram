package com.studygram.repository;

import com.studygram.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/*
 * UserRepository - Handles all database operations for User entity
 *
 * This is an INTERFACE, not a class. Spring automatically creates
 * the implementation for you.
 *
 * JpaRepository<User, Long> means:
 *   - User: the entity this repository manages
 *   - Long: the type of the primary key (id)
 *
 * You get these methods for FREE (no code needed):
 *   - save(user)       → saves a user to database
 *   - findById(id)     → finds user by ID
 *   - findAll()        → gets all users
 *   - deleteById(id)   → deletes a user
 *   - count()          → counts total users
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /*
     * Custom finder method for login with email.
     * Spring reads the method name and creates the query automatically.
     * "findByEmail" becomes → SELECT * FROM users WHERE email = ?
     *
     * Optional means: the result might be empty (user not found)
     */
    Optional<User> findByEmail(String email);

    /*
     * Custom finder method for login with phone number.
     * "findByPhoneNumber" becomes → SELECT * FROM users WHERE phone_number = ?
     */
    Optional<User> findByPhoneNumber(String phoneNumber);

    /*
     * Find user by username (for profile pages).
     * "findByUsername" becomes → SELECT * FROM users WHERE username = ?
     */
    Optional<User> findByUsername(String username);

    /*
     * Check if email already exists (for signup validation).
     * Returns true if email is taken, false if available.
     */
    boolean existsByEmail(String email);

    /*
     * Check if username already exists (for signup validation).
     */
    boolean existsByUsername(String username);

    /*
     * Check if phone number already exists (for signup validation).
     */
    boolean existsByPhoneNumber(String phoneNumber);

    /*
     * Search people by username or display name.
     *
     * The method name is long because Spring Data builds the query from it:
     *   findTop20By  -> LIMIT 20
     *   Username / Name ContainingIgnoreCase -> WHERE LOWER(col) LIKE LOWER('%q%')
     *   Or           -> the two conditions are OR'd
     *
     * Searching both fields matters: people look for each other by the name
     * they know ("Tamanna") as often as by a handle ("toma").
     *
     * The limit is deliberate. Without it, a one-letter query would return every
     * account in the database in a single response.
     */
    List<User> findTop20ByUsernameContainingIgnoreCaseOrNameContainingIgnoreCase(
            String username, String name);

    /*
     * Everyone except one person - the candidate pool for buddy suggestions.
     *
     * SCALE NOTE: suggestions are ranked in memory (see StudyBuddyService),
     * which is fine for a project of this size and would not be at scale. The
     * honest fix is to normalize interests into their own table, the same way
     * post topics were, and let the database do the matching. That refactor is
     * noted in the README rather than pretended away.
     */
    List<User> findByIdNot(Long id);

}
