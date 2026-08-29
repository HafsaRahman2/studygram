package com.studygram.controller;

import com.studygram.dto.CreatePostRequest;
import com.studygram.dto.PostResponse;
import com.studygram.entity.Post;
import com.studygram.service.CommentService;
import com.studygram.service.PostService;
import com.studygram.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 *
 * No endpoint takes the caller's id. That always comes from the JWT.
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
     *   "content": "I learned something today!",
     *   "topics": ["Programming"],
     *   "anonymous": false
     * }
     */
    @PostMapping
    public ResponseEntity<?> createPost(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestBody CreatePostRequest request) {
        try {

            // Create new Post entity from request
            Post post = new Post();
            post.setContent(request.getContent());
            post.setAnonymous(request.isAnonymous());
            post.setTopics(request.getTopics());
            post.setPostType(request.getPostType());

            // Save via service (which validates content and topics)
            // The author is whoever the token says it is, never what the body claims.
            Post savedPost = postService.createPost(me.id(), post);

            // Convert to response (hides user info if anonymous)
            PostResponse response = PostResponse.fromPost(savedPost, 0, me.id());

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
    public ResponseEntity<?> getFeed(@AuthenticationPrincipal AuthenticatedUser me) {

        List<Post> posts = postService.getFeed();

        // toResponses() attaches comment counts and helpful marks in a fixed
        // number of queries, regardless of how many posts came back.
        return ResponseEntity.ok(postService.toResponses(posts, me.id()));
    }

    /*
     * GET PERSONALIZED FEED - Only posts matching user's interests
     *
     * URL: GET /api/posts/feed
     *
     * Returns posts where topic matches user's interests
     * Example:
     *   User interests: "math,science"
     *   Returns: only posts with topic "math" or "science"
     */
    @GetMapping("/feed")
    public ResponseEntity<?> getPersonalizedFeed(@AuthenticationPrincipal AuthenticatedUser me) {
        try {

            // Your personalized feed is yours. There is no id in the URL to change.
            List<Post> posts = postService.getPersonalizedFeed(me.id());

            return ResponseEntity.ok(postService.toResponses(posts, me.id()));

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
            @AuthenticationPrincipal AuthenticatedUser me) {
        try {

            Post post = postService.getPostById(id);
            List<PostResponse> response = postService.toResponses(List.of(post), me.id());

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
            @AuthenticationPrincipal AuthenticatedUser me) {
        try {

            /*
             * userId here says WHOSE posts to show - it is a lookup key, not a
             * claim about who is asking. Who is asking comes from the token,
             * and is what decides which posts show a Delete button.
             */
            List<Post> posts = postService.getPostsByUser(userId);

            return ResponseEntity.ok(postService.toResponses(posts, me.id()));

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * TOGGLE HELPFUL
     *
     * URL: POST /api/posts/5/helpful
     *
     * Toggles helpful: if not marked → mark, if marked → unmark
     * Returns: { "marked": true/false, "helpfulCount": 5, "helpfulUsers": ["user1", "user2"] }
     */
    @PostMapping("/{id}/helpful")
    public ResponseEntity<?> toggleHelpful(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser me) {
        try {

            boolean isMarked = postService.toggleHelpful(id, me.id());
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
     * MARK A QUESTION ANSWERED (or un-mark it)
     *
     * URL: POST /api/posts/5/resolve
     *
     * Only the asker may do this - they are the only one who knows whether an
     * answer actually helped.
     */
    @PostMapping("/{id}/resolve")
    public ResponseEntity<?> toggleResolved(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser me) {
        try {

            Post post = postService.toggleResolved(id, me.id());
            return ResponseEntity.ok(postService.toResponses(List.of(post), me.id()).get(0));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /*
     * DELETE POST
     *
     * URL: DELETE /api/posts/5
     *
     * Who is deleting comes from the token. Only the owner can delete a post,
     * and now the server can actually tell who the owner is.
     */
    /*
     * EDIT A POST
     *
     * URL: PUT /api/posts/5
     * Body: { "content": "...", "topics": ["Physics"] }
     *
     * PUT rather than POST because this replaces the editable parts of an
     * existing thing rather than creating a new one. The id in the URL says
     * WHICH post; the token says who is asking, and the service refuses unless
     * those agree.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updatePost(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestBody CreatePostRequest request) {
        try {

            Post updated = postService.updatePost(
                    id, me.id(), request.getContent(), request.getTopics(),
                    request.getPostType());

            /*
             * Rebuilt through toResponses so the edited post comes back with its
             * comment count and helpful marks intact - the client drops this
             * straight into the list in place of the old one, and anything
             * missing here would look to the user like it had been lost.
             */
            return ResponseEntity.ok(
                    postService.toResponses(List.of(updated), me.id()).get(0));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser me) {
        try {

            /*
             * This used to be DELETE /api/posts/5?userId=1 - and the server
             * believed the number. Changing it to somebody else's id let you
             * delete their posts. The id now comes from the verified token, so
             * the ownership check inside deletePost() finally means something.
             */
            postService.deletePost(id, me.id());
            return ResponseEntity.ok("Post deleted successfully");

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
