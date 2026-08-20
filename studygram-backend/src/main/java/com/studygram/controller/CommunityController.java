package com.studygram.controller;

import com.studygram.dto.PostResponse;
import com.studygram.entity.Community;
import com.studygram.entity.Post;
import com.studygram.service.CommunityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<?> getCommunityPosts(@PathVariable String name) {
        try {

            List<Post> posts = communityService.getCommunityPosts(name);

            List<PostResponse> response = posts.stream()
                    .map(PostResponse::fromPost)
                    .collect(Collectors.toList());

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
