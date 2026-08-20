package com.studygram.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/*
 * Helpful Entity - Tracks who marked which post as helpful
 *
 * This creates a "helpfuls" table in the database.
 * Each row = one user marking one post as helpful
 *
 * Example:
 * | id | user_id | post_id | created_at |
 * | 1  | 1       | 5       | 2026-06-26 |  ← User 1 marked Post 5 as helpful
 * | 2  | 2       | 5       | 2026-06-26 |  ← User 2 marked Post 5 as helpful
 */
@Entity
@Table(name = "helpfuls")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Helpful {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Who marked the post as helpful
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Which post was marked helpful
    @ManyToOne
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // When it was marked
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

}
