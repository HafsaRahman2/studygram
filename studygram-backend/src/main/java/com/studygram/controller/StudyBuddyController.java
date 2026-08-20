package com.studygram.controller;

import com.studygram.dto.UserProfileResponse;
import com.studygram.entity.StudyBuddy;
import com.studygram.entity.User;
import com.studygram.service.StudyBuddyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
 *   GET  /api/buddies/pending/{userId} → Get pending requests
 *   GET  /api/buddies/{userId}         → Get all buddies
 *   DELETE /api/buddies                → Remove buddy
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
     *   "userId": 1,
     *   "buddyId": 2
     * }
     */
    @PostMapping("/request")
    public ResponseEntity<?> sendRequest(@RequestBody Map<String, Long> request) {
        try {

            Long userId = request.get("userId");
            Long buddyId = request.get("buddyId");

            StudyBuddy buddyRequest = studyBuddyService.sendBuddyRequest(userId, buddyId);

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
     * URL: POST /api/buddies/accept/5?userId=2
     */
    @PostMapping("/accept/{requestId}")
    public ResponseEntity<?> acceptRequest(
            @PathVariable Long requestId,
            @RequestParam Long userId) {
        try {

            studyBuddyService.acceptRequest(requestId, userId);
            return ResponseEntity.ok("Buddy request accepted! You are now StudyBuddies.");

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /*
     * REJECT BUDDY REQUEST
     *
     * URL: POST /api/buddies/reject/5?userId=2
     */
    @PostMapping("/reject/{requestId}")
    public ResponseEntity<?> rejectRequest(
            @PathVariable Long requestId,
            @RequestParam Long userId) {
        try {

            studyBuddyService.rejectRequest(requestId, userId);
            return ResponseEntity.ok("Buddy request rejected");

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /*
     * GET PENDING REQUESTS
     *
     * URL: GET /api/buddies/pending/1
     *
     * Returns requests waiting for user to accept/reject
     */
    @GetMapping("/pending/{userId}")
    public ResponseEntity<?> getPendingRequests(@PathVariable Long userId) {
        try {

            List<StudyBuddy> requests = studyBuddyService.getPendingRequests(userId);
            return ResponseEntity.ok(requests);

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * GET SENT REQUESTS
     *
     * URL: GET /api/buddies/sent/1
     *
     * Returns requests user has sent that are pending
     */
    @GetMapping("/sent/{userId}")
    public ResponseEntity<?> getSentRequests(@PathVariable Long userId) {
        try {

            List<StudyBuddy> requests = studyBuddyService.getSentRequests(userId);
            return ResponseEntity.ok(requests);

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * GET ALL BUDDIES
     *
     * URL: GET /api/buddies/1
     *
     * Returns all accepted buddies for a user
     */
    @GetMapping("/{userId}")
    public ResponseEntity<?> getBuddies(@PathVariable Long userId) {
        try {

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
     * URL: GET /api/buddies/count/1
     */
    @GetMapping("/count/{userId}")
    public ResponseEntity<?> getBuddyCount(@PathVariable Long userId) {
        try {

            int count = studyBuddyService.getBuddyCount(userId);
            return ResponseEntity.ok(Map.of("count", count));

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * REMOVE BUDDY
     *
     * URL: DELETE /api/buddies?userId=1&buddyId=2
     */
    @DeleteMapping
    public ResponseEntity<?> removeBuddy(
            @RequestParam Long userId,
            @RequestParam Long buddyId) {
        try {

            studyBuddyService.removeBuddy(userId, buddyId);
            return ResponseEntity.ok("Buddy removed");

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
