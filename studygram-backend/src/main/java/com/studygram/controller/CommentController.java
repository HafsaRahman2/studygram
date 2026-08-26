package com.studygram.controller;

import com.studygram.dto.CommentResponse;
import com.studygram.dto.CreateCommentRequest;
import com.studygram.entity.Comment;
import com.studygram.service.CommentService;
import com.studygram.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/*
 * CommentController - API endpoints for comments
 *
 * Endpoints:
 *   POST   /api/comments              → Add comment to a post
 *   GET    /api/comments/post/{id}    → Get all comments on a post
 *   DELETE /api/comments/{id}         → Delete a comment
 *
 * Who is commenting or deleting always comes from the token, never the URL.
 */
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    @Autowired
    private CommentService commentService;

    /*
     * ADD COMMENT
     *
     * URL: POST /api/comments
     *
     * Request body:
     * {
     *   "postId": 5,
     *   "content": "Great explanation!",
     *   "anonymous": false
     * }
     */
    @PostMapping
    public ResponseEntity<?> addComment(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestBody CreateCommentRequest request) {
        try {

            // Create Comment entity from request
            Comment comment = new Comment();
            comment.setContent(request.getContent());
            comment.setAnonymous(request.isAnonymous());

            // Save via service
            // Author comes from the token, not from the request body.
            Comment savedComment = commentService.addComment(
                    me.id(),
                    request.getPostId(),
                    comment
            );

            // Convert to response (the author is obviously the viewer here)
            CommentResponse response = CommentResponse.fromComment(savedComment, me.id());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /*
     * GET COMMENTS ON A POST
     *
     * URL: GET /api/comments/post/5
     *
     * Returns all comments on post with id 5
     */
    @GetMapping("/post/{postId}")
    public ResponseEntity<?> getComments(
            @PathVariable Long postId,
            @AuthenticationPrincipal AuthenticatedUser me) {
        try {

            List<Comment> comments = commentService.getCommentsByPost(postId);

            // Convert to responses. viewerId decides which comments show a
            // Delete button; it never affects what the server actually permits.
            List<CommentResponse> response = comments.stream()
                    .map(comment -> CommentResponse.fromComment(comment, me.id()))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * GET COMMENT COUNT
     *
     * URL: GET /api/comments/post/5/count
     *
     * Returns: { "count": 10 }
     */
    @GetMapping("/post/{postId}/count")
    public ResponseEntity<?> getCommentCount(@PathVariable Long postId) {
        try {

            int count = commentService.getCommentCount(postId);
            return ResponseEntity.ok(java.util.Map.of("count", count));

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * DELETE COMMENT
     *
     * URL: DELETE /api/comments/5
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteComment(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser me) {
        try {

            commentService.deleteComment(id, me.id());
            return ResponseEntity.ok("Comment deleted successfully");

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
