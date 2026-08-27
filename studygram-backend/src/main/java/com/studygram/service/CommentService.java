package com.studygram.service;

import com.studygram.entity.Comment;
import com.studygram.entity.Post;
import com.studygram.entity.User;
import com.studygram.repository.CommentRepository;
import com.studygram.repository.PostRepository;
import com.studygram.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private AIService aiService;

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
     * ASK THE AI TO ANSWER A QUESTION
     *
     * This is where the two halves of the app meet. Somewhere else you can chat
     * with an AI; somewhere else again you can ask people. Neither is much use
     * on its own at 2am with a deadline: the AI has no idea what your course
     * actually expects, and the humans are asleep.
     *
     * So a question can get an instant AI answer AND stay open for people. You
     * get something to work with immediately, and a real answer when someone
     * wakes up.
     *
     * The AI answer is a Comment like any other, with no author and
     * aiGenerated = true, so it sorts into the thread naturally and every
     * existing count and query keeps working untouched.
     */
    @Transactional
    public Comment addAiAnswer(Long postId, Long requesterId) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        /*
         * Only the asker can trigger this. Every call costs an API request, so
         * letting anyone spend it on anyone else's post would be a way to run
         * up somebody else's bill.
         */
        if (!post.getUser().getId().equals(requesterId)) {
            throw new RuntimeException("Only the person who asked can request an AI answer");
        }

        if (!post.isQuestion()) {
            throw new RuntimeException("Only questions can have an AI answer");
        }

        // Once is enough. Guards against a double-click as much as abuse.
        if (commentRepository.existsByPostAndAiGeneratedTrue(post)) {
            throw new RuntimeException("The AI has already answered this question");
        }

        /*
         * Ask for an explanation rather than a raw chat reply: the prompt in
         * AIService.explain() produces something aimed at a student trying to
         * understand, which is what a question on this site actually is.
         */
        String answer = aiService.explain(post.getContent());

        Comment comment = new Comment();
        comment.setPost(post);
        comment.setUser(null);          // no human wrote this
        comment.setAiGenerated(true);
        comment.setContent(answer);

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

        /*
         * An AI answer has no author, so "the comment owner" does not exist.
         * The person whose question it is can remove it - if the AI produced
         * something unhelpful or wrong, they should not be stuck with it.
         */
        if (comment.getUser() == null) {
            if (!postOwnerId.equals(userId)) {
                throw new RuntimeException("Only the person who asked can remove an AI answer");
            }
            commentRepository.deleteById(commentId);
            return;
        }

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
