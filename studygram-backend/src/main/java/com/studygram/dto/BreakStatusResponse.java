package com.studygram.dto;

import lombok.Data;

import java.time.LocalDateTime;

/*
 * BreakStatusResponse - Everything the UI needs to draw the break screen
 *
 * One endpoint answers the whole question, so the frontend never has to work
 * out the rules for itself. That matters: if the browser decided "can I take a
 * break yet?", the answer would be trivially editable from the console, and the
 * rule would exist in two places at once.
 *
 * The three states this can describe:
 *
 *   ON A BREAK       state = "ACTIVE"
 *                    secondsRemaining counts down, canExtend says whether the
 *                    one +5 minutes is still available
 *
 *   COOLING DOWN     state = "COOLDOWN"
 *                    secondsUntilAvailable counts down to the next break
 *
 *   READY            state = "AVAILABLE"
 *                    the button is live
 */
@Data
public class BreakStatusResponse {

    /* "ACTIVE", "COOLDOWN" or "AVAILABLE" */
    private String state;

    /* Seconds left in the current break. Only meaningful when ACTIVE. */
    private long secondsRemaining;

    /* Seconds until a new break is allowed. Only meaningful when COOLDOWN. */
    private long secondsUntilAvailable;

    /* Whether the single +5 minute extension is still unused. ACTIVE only. */
    private boolean canExtend;

    /*
     * The absolute moment the break ends.
     *
     * Sent alongside secondsRemaining so the browser can keep a smooth
     * countdown running locally without polling the server every second, while
     * still being anchored to a time the server chose.
     */
    private LocalDateTime endsAt;

    /* How many breaks the user has taken today - a gentle bit of self-awareness. */
    private int breaksToday;

    /* How long a fresh break lasts, in minutes. Lets the UI say "5" without hardcoding it. */
    private int breakMinutes;

    /* How long the wait between breaks is, in minutes. */
    private int cooldownMinutes;

}
