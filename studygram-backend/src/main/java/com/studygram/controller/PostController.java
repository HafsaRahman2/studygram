package com.studygram.controller;

import com.studygram.dto.CreatePostRequest;
import com.studygram.dto.PostResponse;
import com.studygram.entity.Post;
import com.studygram.service.CommentService;
import com.studygram.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/*
 * PostController - Handles all HTTP requests for Posts
 *
 * Endpoints:
 *   POST   /api/posts           → Create a new post
 *   GET    /api/posts           → Get all posts (feed)
 *   GET    /api/posts/{id}      → Get single post
 *   GET    /api/posts/user/{id} → Get posts by user
 *   POST   /api/posts/{id}/helpful → Mark post as helpful
 *   DELETE /api/posts/{id}      → Delete a post
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    @Autowired
    private PostService postService;

    @Autowired
    private CommentService commentService;

    /*
     * CREATE POST
     *
     * URL: POST /api/posts
     *
     * Request body:
     * {
     *   "userId": 1,
     *   "content": "I learned something today!",
     *   "anonymous": false
     * }
     */
    @PostMapping
    public ResponseEntity<?> createPost(@RequestBody CreatePostRequest request) {
        try {

            // Create new Post entity from request
            Post post = new Post();
            post.setContent(request.getContent());
            post.setAnonymous(request.isAnonymous());
            post.setTopics(request.getTopics());

            // Save via service (which validates content and topics)
            Post savedPost = postService.createPost(request.getUserId(), post);

            // Convert to response (hides user info if anonymous)
            PostResponse response = PostResponse.fromPost(savedPost, 0, request.getUserId());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /*
     * GET FEED - All posts, newest first
     *
     * URL: GET /api/posts
     *
     * Returns list of posts (anonymous posts show "Anonymous" as author)
     */
    @GetMapping
    public ResponseEntity<?> getFeed(@RequestParam(required = false) Long viewerId) {

        List<Post> posts = postService.getFeed();

        // toResponses() attaches comment counts and helpful marks in a fixed
        // number of queries, regardless of how many posts came back.
        return ResponseEntity.ok(postService.toResponses(posts, viewerId));
    }

    /*
     * GET PERSONALIZED FEED - Only posts matching user's interests
     *
     * URL: GET /api/posts/feed/1
     *
     * Returns posts where topic matches user's interests
     * Example:
     *   User interests: "math,science"
     *   Returns: only posts with topic "math" or "science"
     */
    @GetMapping("/feed/{userId}")
    public ResponseEntity<?> getPersonalizedFeed(@PathVariable Long userId) {
        try {

            List<Post> posts = postService.getPersonalizedFeed(userId);

            // The requesting user is also the viewer here
            return ResponseEntity.ok(postService.toResponses(posts, userId));

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * GET SINGLE POST
     *
     * URL: GET /api/posts/5
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getPost(
            @PathVariable Long id,
            @RequestParam(required = false) Long viewerId) {
        try {

            Post post = postService.getPostById(id);
            List<PostResponse> response = postService.toResponses(List.of(post), viewerId);

            return ResponseEntity.ok(response.get(0));

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * GET USER'S POSTS
     *
     * URL: GET /api/posts/user/1
     *
     * Shows all posts by a specific user
     * Note: Anonymous posts will still show as "Anonymous" to others
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getPostsByUser(
            @PathVariable Long userId,
            @RequestParam(required = false) Long viewerId) {
        try {

            List<Post> posts = postService.getPostsByUser(userId);

            return ResponseEntity.ok(postService.toResponses(posts, viewerId));

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * TOGGLE HELPFUL
     *
     * URL: POST /api/posts/5/helpful?userId=1
     *
     * Toggles helpful: if not marked → mark, if marked → unmark
     * Returns: { "marked": true/false, "helpfulCount": 5, "helpfulUsers": ["user1", "user2"] }
     */
    @PostMapping("/{id}/helpful")
    public ResponseEntity<?> toggleHelpful(
            @PathVariable Long id,
            @RequestParam Long userId) {
        try {

            boolean isMarked = postService.toggleHelpful(id, userId);
            Post post = postService.getPostById(id);
            java.util.List<String> helpfulUsers = postService.getHelpfulUsers(id);

            return ResponseEntity.ok(java.util.Map.of(
                "marked", isMarked,
                "helpfulCount", post.getHelpfulCount(),
                "helpfulUsers", helpfulUsers
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * GET HELPFUL USERS
     *
     * URL: GET /api/posts/5/helpful
     *
     * Returns list of usernames who marked this post as helpful
     */
    @GetMapping("/{id}/helpful")
    public ResponseEntity<?> getHelpfulUsers(@PathVariable Long id) {
        try {

            java.util.List<String> users = postService.getHelpfulUsers(id);
            return ResponseEntity.ok(users);

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * DELETE POST
     *
     * URL: DELETE /api/posts/5?userId=1
     *
     * The userId query param tells us who is trying to delete
     * Only the owner can delete their post
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(
            @PathVariable Long id,
            @RequestParam Long userId) {
        try {

            postService.deletePost(id, userId);
            return ResponseEntity.ok("Post deleted successfully");

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
