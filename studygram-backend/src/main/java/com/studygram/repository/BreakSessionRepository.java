package com.studygram.repository;

import com.studygram.entity.BreakSession;
import com.studygram.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/*
 * BreakSessionRepository - Database operations for break sessions
 */
public interface BreakSessionRepository extends JpaRepository<BreakSession, Long> {

    /*
     * The user's most recent break, whatever its state.
     *
     * "findTop1By...OrderBy..." is Spring Data's way of saying LIMIT 1 - it
     * builds the query from the method name. Almost every decision this feature
     * makes ("are you on a break?", "when can you take the next one?") comes
     * down to this single row, so there is no reason to load the rest.
     */
    Optional<BreakSession> findTop1ByUserOrderByStartedAtDesc(User user);

    /*
     * Breaks taken since a given moment, newest first.
     * Used for the "you have taken N breaks today" line.
     */
    List<BreakSession> findByUserAndStartedAtAfterOrderByStartedAtDesc(
            User user, LocalDateTime after);

}
