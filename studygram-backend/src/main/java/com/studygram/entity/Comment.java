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
     * Who wrote this comment - NULL when it was written by the AI assistant.
     *
     * The AI is not a user. Giving it a row in the users table would mean an
     * account with a password hash that nobody can log into, showing up in
     * search results and buddy suggestions, needing to be excluded from a dozen
     * queries that have no business knowing it exists.
     *
     * A null author plus the flag below is more honest: this comment has no
     * human behind it, and every piece of code that renders an author has to
     * confront that fact rather than quietly getting it wrong.
     */
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    /*
     * True when the AI assistant wrote this answer.
     *
     * Always surfaced in the UI. Someone reading an answer needs to know
     * whether a person or a language model wrote it - they carry different
     * kinds of trust, and blurring that would be the single most dishonest
     * thing this app could do.
     */
    @Column(nullable = false)
    private boolean aiGenerated = false;

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
