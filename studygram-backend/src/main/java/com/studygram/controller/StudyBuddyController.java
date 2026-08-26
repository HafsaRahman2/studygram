package com.studygram.controller;

import com.studygram.dto.UserProfileResponse;
import com.studygram.entity.StudyBuddy;
import com.studygram.entity.User;
import com.studygram.service.StudyBuddyService;
import com.studygram.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/*
 * StudyBuddyController - API endpoints for buddy connections
 *
 * Endpoints:
 *   POST /api/buddies/request          → Send buddy request
 *   POST /api/buddies/accept/{id}      → Accept request
 *   POST /api/buddies/reject/{id}      → Reject request
 *   GET  /api/buddies/pending          → Get pending requests
 *   GET  /api/buddies                  → Get all buddies
 *   DELETE /api/buddies?buddyId=       → Remove buddy
 *
 * None of these take a user id for the CALLER - that always comes from the
 * token. Ids in these URLs refer to the other party, or to a request being
 * acted on.
 */
@RestController
@RequestMapping("/api/buddies")
public class StudyBuddyController {

    @Autowired
    private StudyBuddyService studyBuddyService;

    /*
     * SEND BUDDY REQUEST
     *
     * URL: POST /api/buddies/request
     *
     * Request body:
     * {
     *   "buddyId": 2
     * }
     */
    @PostMapping("/request")
    public ResponseEntity<?> sendRequest(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestBody Map<String, Long> request) {
        try {

            // You can only send requests as yourself.
            Long buddyId = request.get("buddyId");

            StudyBuddy buddyRequest = studyBuddyService.sendBuddyRequest(me.id(), buddyId);

            return ResponseEntity.ok(Map.of(
                    "message", "Buddy request sent",
                    "requestId", buddyRequest.getId()
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /*
     * ACCEPT BUDDY REQUEST
     *
     * URL: POST /api/buddies/accept/5
     */
    @PostMapping("/accept/{requestId}")
    public ResponseEntity<?> acceptRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal AuthenticatedUser me) {
        try {

            studyBuddyService.acceptRequest(requestId, me.id());
            return ResponseEntity.ok("Buddy request accepted! You are now StudyBuddies.");

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /*
     * REJECT BUDDY REQUEST
     *
     * URL: POST /api/buddies/reject/5
     */
    @PostMapping("/reject/{requestId}")
    public ResponseEntity<?> rejectRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal AuthenticatedUser me) {
        try {

            studyBuddyService.rejectRequest(requestId, me.id());
            return ResponseEntity.ok("Buddy request rejected");

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /*
     * GET PENDING REQUESTS
     *
     * URL: GET /api/buddies/pending
     *
     * Returns requests waiting for you to accept/reject
     */
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingRequests(@AuthenticationPrincipal AuthenticatedUser me) {
        try {

            List<StudyBuddy> requests = studyBuddyService.getPendingRequests(me.id());
            return ResponseEntity.ok(requests);

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * GET SENT REQUESTS
     *
     * URL: GET /api/buddies/sent
     *
     * Returns requests you have sent that are still pending
     */
    @GetMapping("/sent")
    public ResponseEntity<?> getSentRequests(@AuthenticationPrincipal AuthenticatedUser me) {
        try {

            List<StudyBuddy> requests = studyBuddyService.getSentRequests(me.id());
            return ResponseEntity.ok(requests);

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * GET ALL BUDDIES
     *
     * URL: GET /api/buddies
     *
     * Returns all your accepted buddies
     */
    @GetMapping
    public ResponseEntity<?> getBuddies(@AuthenticationPrincipal AuthenticatedUser me) {
        try {

            Long userId = me.id();
            List<User> buddies = studyBuddyService.getBuddies(userId);

            /*
             * Map to the profile DTO rather than returning entities. Being
             * someone's study buddy does not override their privacy settings -
             * a buddy who hides their phone number still hides it here.
             */
            List<UserProfileResponse> response = buddies.stream()
                    .map(buddy -> UserProfileResponse.of(buddy, userId))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * GET BUDDY COUNT
     *
     * URL: GET /api/buddies/count
     */
    @GetMapping("/count")
    public ResponseEntity<?> getBuddyCount(@AuthenticationPrincipal AuthenticatedUser me) {
        try {

            int count = studyBuddyService.getBuddyCount(me.id());
            return ResponseEntity.ok(Map.of("count", count));

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * REMOVE BUDDY
     *
     * URL: DELETE /api/buddies?buddyId=2
     */
    @DeleteMapping
    public ResponseEntity<?> removeBuddy(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam Long buddyId) {
        try {

            studyBuddyService.removeBuddy(me.id(), buddyId);
            return ResponseEntity.ok("Buddy removed");

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
