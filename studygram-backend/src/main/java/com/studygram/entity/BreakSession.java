package com.studygram.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/*
 * BreakSession Entity - One "Take a break" session
 *
 * THE FEATURE
 *
 * Studying for hours without stopping is bad for you and bad for retention.
 * "Take a break" gives students a deliberate 5 minute pause, then puts the
 * button on cooldown for an hour so the break stays a break instead of becoming
 * the activity.
 *
 * WHY THIS LIVES IN THE DATABASE AND NOT IN THE BROWSER
 *
 * The obvious implementation is a timestamp in localStorage. It is also
 * useless: clearing site data, opening a private window, or switching to your
 * phone resets the cooldown instantly. A limit the user can erase is decoration.
 *
 * Storing sessions server-side means the cooldown is real, and it follows the
 * student across devices. It also gives them an honest history of when they
 * actually rested.
 */
@Entity
@Table(name = "break_sessions")
@Data
@NoArgsConstructor
public class BreakSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /* When the break started. */
    @Column(nullable = false)
    private LocalDateTime startedAt;

    /*
     * When the break is scheduled to end.
     *
     * Stored as an absolute moment rather than counting down in the browser.
     * A countdown held in JavaScript stops when the tab is backgrounded, drifts
     * when the laptop sleeps, and can be edited from the console. An end time
     * on the server is none of those things - the client just renders the
     * difference between now and this.
     */
    @Column(nullable = false)
    private LocalDateTime endsAt;

    /*
     * Set when the break actually finished - either the timer ran out, or the
     * student pressed "I'm ready" early.
     *
     * Null means the break is still running.
     */
    private LocalDateTime endedAt;

    /*
     * Whether the one allowed "+5 more minutes" extension has been used.
     *
     * The default break is 5 minutes because a short break is one people
     * actually take and actually come back from. The extension exists for the
     * days when 5 is genuinely not enough - but it is a deliberate choice, used
     * once, rather than the default.
     */
    @Column(nullable = false)
    private boolean extended = false;

    /*
     * Is this break running right now?
     *
     * Both conditions matter: not manually ended, AND not past its end time.
     * Checking only endedAt would leave a break "active" forever if the student
     * closed the tab and never came back.
     */
    public boolean isActive() {
        return endedAt == null && LocalDateTime.now().isBefore(endsAt);
    }

    /*
     * The moment this break actually finished.
     *
     * If the student ended it early, that is endedAt. If they simply walked
     * away and the timer expired, it is endsAt. The cooldown is measured from
     * whichever came first, so nobody is penalised for closing the tab.
     */
    public LocalDateTime effectiveEnd() {
        if (endedAt != null && endedAt.isBefore(endsAt)) {
            return endedAt;
        }
        return endsAt;
    }

    /* How many whole seconds are left, floored at zero. */
    public long secondsRemaining() {
        long seconds = Duration.between(LocalDateTime.now(), endsAt).getSeconds();
        return Math.max(0, seconds);
    }

}
