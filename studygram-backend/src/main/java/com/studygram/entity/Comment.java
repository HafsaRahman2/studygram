package com.studygram.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/*
 * Comment Entity - A reply to a post
 *
 * Each comment belongs to:
 *   - One User (who wrote it)
 *   - One Post (which post it's on)
 *
 * Comments can also be anonymous, just like posts
 */
@Entity
@Table(name = "comments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Comment {

    /*
     * Unique ID for each comment
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * The comment text
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /*
     * WHO wrote this comment
     *
     * @ManyToOne: Many comments can be written by one user
     */
    /*
     * WHO wrote this comment. Always a person.
     *
     * This was briefly nullable, to allow comments authored by the AI. That
     * feature is gone: an AI answer sitting at the top of a question stopped
     * people writing their own, and the community answers are the part of this
     * app that is actually hard to build. The AI assistant covers the same need
     * better, in a conversation, on its own page.
     *
     * Back to non-null, because every comment now has a human behind it.
     */
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /*
     * WHICH POST this comment is on
     *
     * @ManyToOne: Many comments can be on one post
     */
    @ManyToOne
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    /*
     * When the comment was created
     */
    private LocalDateTime createdAt;

    /*
     * Anonymous comment option
     * Same as posts - hide user identity if true
     */
    private boolean anonymous = false;

    /*
     * Auto-set createdAt before saving
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

}
