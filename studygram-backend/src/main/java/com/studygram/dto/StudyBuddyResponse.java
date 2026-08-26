package com.studygram.dto;

import com.studygram.entity.StudyBuddy;
import com.studygram.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/*
 * StudyBuddyResponse - A buddy request, as seen by one side of it
 *
 * WHY THIS CLASS HAD TO EXIST BEFORE ANY UI COULD BE BUILT
 *
 * /api/buddies/pending and /sent used to return StudyBuddy ENTITIES directly.
 * A StudyBuddy holds two User objects, and Jackson happily serialized all of
 * both — which meant the response contained:
 *
 *     "password": "$2a$10$EXAMPLE.HASH.NOT.A.REAL.ONE..."
 *
 * BCrypt hashes for every person in your requests list, plus their email and
 * phone number regardless of the privacy switches they had set.
 *
 * Hashes are not passwords, but publishing them is still bad: an attacker can
 * take them away and brute-force offline, at their own pace, with no login
 * attempts for anyone to notice.
 *
 * This is the same lesson as PostResponse and UserProfileResponse, for the
 * third time: NEVER SERIALIZE AN ENTITY. A DTO cannot leak a field it does not
 * have.
 *
 * WHAT "OTHER USER" MEANS
 *
 * A StudyBuddy row records a direction — `user` sent, `buddy` received. But
 * whoever is looking already knows who they are; what they want to see is the
 * PERSON ON THE OTHER END. So this DTO resolves that, and reports the direction
 * separately.
 */
@Data
public class StudyBuddyResponse {

    /* Id of the request itself, needed to accept or reject it. */
    private Long requestId;

    /*
     * The other person, run through the normal profile DTO so their privacy
     * settings still apply. Being in someone's request list does not entitle
     * you to fields they have hidden.
     */
    private UserProfileResponse user;

    /* PENDING, ACCEPTED or REJECTED. */
    private String status;

    /*
     * "INCOMING" - they asked you, so you can accept or reject
     * "OUTGOING" - you asked them, so you can only wait or cancel
     *
     * The UI needs this to decide which buttons to draw, and it cannot work it
     * out from the payload without knowing which side it is on.
     */
    private String direction;

    private LocalDateTime createdAt;

    /*
     * Build the response from the perspective of one viewer.
     *
     * @param connection the stored request
     * @param viewerId   who is looking - decides who counts as "the other user"
     */
    public static StudyBuddyResponse of(StudyBuddy connection, Long viewerId) {

        StudyBuddyResponse response = new StudyBuddyResponse();

        response.setRequestId(connection.getId());
        response.setStatus(connection.getStatus());
        response.setCreatedAt(connection.getCreatedAt());

        boolean viewerSentIt = connection.getUser().getId().equals(viewerId);

        User other = viewerSentIt ? connection.getBuddy() : connection.getUser();

        response.setDirection(viewerSentIt ? "OUTGOING" : "INCOMING");
        response.setUser(UserProfileResponse.of(other, viewerId));

        return response;
    }

}
