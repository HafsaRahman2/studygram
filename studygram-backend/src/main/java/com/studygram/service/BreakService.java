package com.studygram.service;

import com.studygram.dto.BreakStatusResponse;
import com.studygram.entity.BreakSession;
import com.studygram.entity.User;
import com.studygram.repository.BreakSessionRepository;
import com.studygram.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/*
 * BreakService - The rules behind "Take a break"
 *
 * THE RULES
 *
 *   1. A break lasts 5 minutes.
 *   2. You may extend it by 5 more, once per break.
 *   3. After a break ends, the button is locked for 1 hour.
 *
 * WHY THE COOLDOWN STARTS WHEN THE BREAK ENDS
 *
 * The tempting implementation is "one break per hour", measured from when the
 * break started. But then a 10 minute break leaves only 50 minutes of studying
 * before the next one is available - and extending your break would shorten
 * your work period, which is exactly backwards.
 *
 * Measuring from the END means the study period between breaks is always a full
 * hour, whether the break was 5 minutes or 10.
 */
@Service
public class BreakService {

    @Autowired
    private BreakSessionRepository breakRepository;

    @Autowired
    private UserRepository userRepository;

    /*
     * The three numbers that define the feature, read from configuration.
     *
     * These are exactly the kind of value that gets tuned after watching real
     * people use it, so they should not require a recompile. Defaults match the
     * design: 5 minute break, 5 minute extension, 1 hour between breaks.
     */
    @Value("${studygram.break.minutes:5}")
    private int breakMinutes;

    @Value("${studygram.break.extension-minutes:5}")
    private int extensionMinutes;

    @Value("${studygram.break.cooldown-minutes:60}")
    private int cooldownMinutes;

    /*
     * GET STATUS - which of the three states is this user in?
     */
    public BreakStatusResponse getStatus(Long userId) {

        User user = findUser(userId);
        Optional<BreakSession> latest = breakRepository.findTop1ByUserOrderByStartedAtDesc(user);

        BreakStatusResponse status = new BreakStatusResponse();
        status.setBreakMinutes(breakMinutes);
        status.setCooldownMinutes(cooldownMinutes);
        status.setBreaksToday(countBreaksToday(user));

        // Never taken a break, or the last one was long ago
        if (latest.isEmpty()) {
            status.setState("AVAILABLE");
            return status;
        }

        BreakSession session = latest.get();

        // Currently on a break
        if (session.isActive()) {
            status.setState("ACTIVE");
            status.setSecondsRemaining(session.secondsRemaining());
            status.setEndsAt(session.getEndsAt());
            status.setCanExtend(!session.isExtended());
            return status;
        }

        // Break is over - is the cooldown still running?
        LocalDateTime availableAt = session.effectiveEnd().plusMinutes(cooldownMinutes);
        long secondsToWait = Duration.between(LocalDateTime.now(), availableAt).getSeconds();

        if (secondsToWait > 0) {
            status.setState("COOLDOWN");
            status.setSecondsUntilAvailable(secondsToWait);
            return status;
        }

        status.setState("AVAILABLE");
        return status;
    }

    /*
     * START A BREAK
     *
     * The cooldown is enforced here, on the server. The UI also disables the
     * button during a cooldown, but that is a courtesy - this is the check that
     * counts, because it is the only one a determined user cannot skip by
     * calling the API directly.
     */
    @Transactional
    public BreakStatusResponse startBreak(Long userId) {

        User user = findUser(userId);
        BreakStatusResponse current = getStatus(userId);

        if ("ACTIVE".equals(current.getState())) {
            throw new RuntimeException("You are already on a break");
        }

        if ("COOLDOWN".equals(current.getState())) {
            throw new RuntimeException(
                    "Next break available in " + formatWait(current.getSecondsUntilAvailable())
            );
        }

        LocalDateTime now = LocalDateTime.now();

        BreakSession session = new BreakSession();
        session.setUser(user);
        session.setStartedAt(now);
        session.setEndsAt(now.plusMinutes(breakMinutes));

        breakRepository.save(session);

        return getStatus(userId);
    }

    /*
     * EXTEND - the one allowed "+5 more minutes"
     */
    @Transactional
    public BreakStatusResponse extendBreak(Long userId) {

        User user = findUser(userId);

        BreakSession session = breakRepository.findTop1ByUserOrderByStartedAtDesc(user)
                .orElseThrow(() -> new RuntimeException("You are not on a break"));

        if (!session.isActive()) {
            throw new RuntimeException("Your break has already finished");
        }

        if (session.isExtended()) {
            throw new RuntimeException("You have already extended this break");
        }

        session.setEndsAt(session.getEndsAt().plusMinutes(extensionMinutes));
        session.setExtended(true);
        breakRepository.save(session);

        return getStatus(userId);
    }

    /*
     * END EARLY - "I'm ready to go back"
     *
     * Worth having its own action rather than just navigating away: finishing
     * early starts the cooldown early too, so a student who only needed two
     * minutes is not punished for it.
     */
    @Transactional
    public BreakStatusResponse endBreak(Long userId) {

        User user = findUser(userId);

        BreakSession session = breakRepository.findTop1ByUserOrderByStartedAtDesc(user)
                .orElseThrow(() -> new RuntimeException("You are not on a break"));

        if (session.getEndedAt() == null) {
            session.setEndedAt(LocalDateTime.now());
            breakRepository.save(session);
        }

        return getStatus(userId);
    }

    /* ------------------------------------------------------------ helpers */

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /*
     * Breaks started since midnight.
     *
     * atStartOfDay() uses the server's timezone, which is a simplification -
     * a student in a different timezone sees their day roll over at the wrong
     * moment. Storing each user's timezone would fix it properly.
     */
    private int countBreaksToday(User user) {
        LocalDateTime midnight = LocalDate.now().atStartOfDay();
        return breakRepository
                .findByUserAndStartedAtAfterOrderByStartedAtDesc(user, midnight)
                .size();
    }

    /* Turn 2,400 seconds into "40 minutes" for an error message. */
    private String formatWait(long seconds) {
        long minutes = Math.max(1, Math.round(seconds / 60.0));
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }

}
