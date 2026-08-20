package com.studygram.service;

import com.studygram.entity.Comment;
import com.studygram.entity.Post;
import com.studygram.entity.User;
import com.studygram.repository.CommentRepository;
import com.studygram.repository.PostRepository;
import com.studygram.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/*
 * CommentService - Business logic for comments
 */
@Service
public class CommentService {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    /*
     * ADD COMMENT to a post
     *
     * Takes: userId, postId, comment content, anonymous flag
     * Returns: saved comment
     */
    public Comment addComment(Long userId, Long postId, Comment comment) {

        // Find the user who is commenting
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Find the post being commented on
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        // Link comment to user and post
        comment.setUser(user);
        comment.setPost(post);

        // Save and return
        return commentRepository.save(comment);
    }

    /*
     * GET COMMENTS on a post
     *
     * Returns all comments on a specific post, oldest first
     */
    public List<Comment> getCommentsByPost(Long postId) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        return commentRepository.findByPostOrderByCreatedAtAsc(post);
    }

    /*
     * COUNT COMMENTS on a post
     */
    public int getCommentCount(Long postId) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        return commentRepository.countByPost(post);
    }

    /*
     * DELETE COMMENT
     *
     * Can be deleted by:
     * 1. The comment owner (who wrote it)
     * 2. The post owner (who owns the post)
     */
    public void deleteComment(Long commentId, Long userId) {

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        // Get the post owner's ID
        Long postOwnerId = comment.getPost().getUser().getId();

        // Get the comment owner's ID
        Long commentOwnerId = comment.getUser().getId();

        // Check if user is either comment owner or post owner
        boolean isCommentOwner = commentOwnerId.equals(userId);
        boolean isPostOwner = postOwnerId.equals(userId);

        if (!isCommentOwner && !isPostOwner) {
            throw new RuntimeException("You can only delete your own comments or comments on your posts");
        }

        commentRepository.deleteById(commentId);
    }

}
