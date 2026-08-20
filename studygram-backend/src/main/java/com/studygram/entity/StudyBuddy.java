package com.studygram.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/*
 * StudyBuddy Entity - Represents a friendship/connection between two users
 *
 * Like Facebook friends or Instagram followers, but called "StudyBuddies"
 *
 * How it works:
 *   - User A sends buddy request to User B
 *   - User B accepts → they become StudyBuddies
 *   - Both can see each other's posts on their profiles
 *
 * Database stores:
 *   | id | user_id | buddy_id | status   | created_at |
 *   | 1  | 1       | 2        | ACCEPTED | 2026-06-25 |
 *
 *   This means: User 1 and User 2 are StudyBuddies
 */
@Entity
@Table(name = "study_buddies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudyBuddy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * The user who sent the buddy request
     */
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /*
     * The user who received the buddy request
     */
    @ManyToOne
    @JoinColumn(name = "buddy_id", nullable = false)
    private User buddy;

    /*
     * Status of the buddy request:
     *   - PENDING: Request sent, waiting for response
     *   - ACCEPTED: Both are now StudyBuddies
     *   - REJECTED: Request was declined
     */
    @Column(nullable = false)
    private String status = "PENDING";

    /*
     * When the request was created
     */
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

}
