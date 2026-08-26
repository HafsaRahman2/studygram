package com.studygram.controller;

import com.studygram.dto.StudyBuddyResponse;
import com.studygram.dto.UserProfileResponse;
import com.studygram.dto.UserSearchResult;
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
 *   GET  /api/buddies/search?q=        → Find people to add
 *   GET  /api/buddies/suggestions      → People who share your interests
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

            /*
             * SECURITY FIX: this used to return the StudyBuddy entities
             * directly. Each one holds two full User objects, so the response
             * contained BCrypt password hashes, plus emails and phone numbers
             * that those users had marked private.
             *
             * Mapping through a DTO makes that impossible - a class without a
             * password field cannot serialize one.
             */
            List<StudyBuddyResponse> response = studyBuddyService.getPendingRequests(me.id())
                    .stream()
                    .map(request -> StudyBuddyResponse.of(request, me.id()))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);

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

            // Same DTO mapping as /pending - never serialize the entity.
            List<StudyBuddyResponse> response = studyBuddyService.getSentRequests(me.id())
                    .stream()
                    .map(request -> StudyBuddyResponse.of(request, me.id()))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);

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
     * SEARCH PEOPLE
     *
     * URL: GET /api/buddies/search?q=tam
     *
     * Each result carries your relationship to that person, so the UI knows
     * whether to offer Add, Pending, Accept, or nothing at all. Queries shorter
     * than two characters return an empty list rather than most of the database.
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(name = "q", required = false) String query) {
        try {

            List<UserSearchResult> results = studyBuddyService.searchUsers(me.id(), query);
            return ResponseEntity.ok(results);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /*
     * SUGGESTED BUDDIES
     *
     * URL: GET /api/buddies/suggestions
     *
     * People who share interests with you, most overlap first, excluding
     * anyone you are already connected to. Returns an empty list if you have
     * not set any interests - there is nothing to match on.
     */
    @GetMapping("/suggestions")
    public ResponseEntity<?> suggestBuddies(@AuthenticationPrincipal AuthenticatedUser me) {
        try {

            return ResponseEntity.ok(studyBuddyService.suggestBuddies(me.id(), 10));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
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
