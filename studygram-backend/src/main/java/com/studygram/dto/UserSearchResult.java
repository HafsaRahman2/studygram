package com.studygram.dto;

import lombok.Data;

import java.util.List;

/*
 * UserSearchResult - One person in "find study buddies", plus your relationship
 * to them
 *
 * WHY THE RELATIONSHIP TRAVELS WITH THE USER
 *
 * A search result needs a button, and which button depends on where you two
 * already stand: Add, Pending, Accept, or already Buddies.
 *
 * The frontend could work that out by fetching your buddies and both request
 * lists and cross-referencing every result against all three. That is three
 * extra requests and a rule reimplemented in the browser — the exact pattern
 * that produced this project's earlier bugs.
 *
 * So the server answers it once, per result.
 */
@Data
public class UserSearchResult {

    /* The person, with their privacy settings already applied. */
    private UserProfileResponse user;

    /*
     * Where you stand with them:
     *
     *   SELF             - this is you
     *   NONE             - no connection; offer "Add buddy"
     *   REQUEST_SENT     - you asked them; show "Pending"
     *   REQUEST_RECEIVED - they asked you; offer "Accept"
     *   BUDDIES          - already connected
     *   REJECTED         - a previous request was declined
     *
     * A string rather than an enum on the wire so adding a state later does not
     * break older clients that have not been redeployed.
     */
    private String relationship;

    /*
     * The pending request's id, when there is one. Lets the UI accept an
     * incoming request straight from search results, without another lookup.
     */
    private Long requestId;

    /*
     * Topics you both listed as interests.
     *
     * This is the entire premise of the app — "find people studying the same
     * thing" — so showing WHY someone is a good match matters more than showing
     * that they exist. A result reading "3 shared interests: Programming,
     * Mathematics, Physics" is a reason to connect; a bare username is not.
     */
    private List<String> sharedInterests;

}
