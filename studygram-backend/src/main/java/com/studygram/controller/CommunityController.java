package com.studygram.controller;

import com.studygram.dto.PostResponse;
import com.studygram.entity.Community;
import com.studygram.entity.Post;
import com.studygram.security.AuthenticatedUser;
import com.studygram.service.CommunityService;
import com.studygram.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/*
 * CommunityController - API endpoints for Communities
 *
 * Endpoints:
 *   GET  /api/communities              → Get all communities
 *   GET  /api/communities/user/{id}    → Get user's communities (based on interests)
 *   GET  /api/communities/{name}       → Get community details
 *   GET  /api/communities/{name}/posts → Get posts in a community
 *   POST /api/communities              → Create new community (admin)
 */
@RestController
@RequestMapping("/api/communities")
public class CommunityController {

    @Autowired
    private CommunityService communityService;

    /* Used to build post responses the same way the feed does. */
    @Autowired
    private PostService postService;

    /*
     * GET ALL COMMUNITIES
     *
     * URL: GET /api/communities
     *
     * Returns all available communities
     */
    @GetMapping
    public ResponseEntity<?> getAllCommunities() {
        List<Community> communities = communityService.getAllCommunities();
        return ResponseEntity.ok(communities);
    }

    /*
     * GET USER'S COMMUNITIES
     *
     * URL: GET /api/communities/user/1
     *
     * Returns communities the user has joined (based on interests)
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserCommunities(@PathVariable Long userId) {
        try {

            List<Community> communities = communityService.getUserCommunities(userId);
            return ResponseEntity.ok(communities);

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * GET COMMUNITY DETAILS
     *
     * URL: GET /api/communities/math
     */
    @GetMapping("/{name}")
    public ResponseEntity<?> getCommunity(@PathVariable String name) {
        try {

            Community community = communityService.getCommunityByName(name);
            return ResponseEntity.ok(community);

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * GET POSTS IN COMMUNITY
     *
     * URL: GET /api/communities/math/posts
     *
     * Returns all posts with topic = "math"
     */
    @GetMapping("/{name}/posts")
    public ResponseEntity<?> getCommunityPosts(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable String name) {
        try {

            List<Post> posts = communityService.getCommunityPosts(name);

            /*
             * Build the responses through PostService, exactly like the feed does.
             *
             * This used to map with the bare PostResponse::fromPost, which fills in
             * a comment count of 0, no helpful marks and no viewer. The result was
             * that the SAME post looked different depending on how you reached it:
             * "1 answer" in the feed, "0 answers" when browsing by topic, and your
             * own posts lost their Delete and Mark-resolved buttons because
             * `ownPost` was computed against a viewer of null.
             *
             * toResponses does it in a fixed number of queries rather than a few
             * per post, so this is also the faster path - there was never a reason
             * to have a second one.
             */
            List<PostResponse> response =
                    postService.toResponses(posts, me == null ? null : me.id());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * CREATE COMMUNITY
     *
     * URL: POST /api/communities
     *
     * Request body:
     * {
     *   "name": "math",
     *   "displayName": "Math",
     *   "description": "Discuss algebra, calculus, statistics and more"
     * }
     */
    @PostMapping
    public ResponseEntity<?> createCommunity(@RequestBody Community community) {
        try {

            Community saved = communityService.createCommunity(community);
            return ResponseEntity.ok(saved);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
