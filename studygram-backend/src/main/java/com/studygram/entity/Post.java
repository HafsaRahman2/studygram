package com.studygram.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/*
 * Post Entity - Represents a learning post shared by a user
 *
 * This creates a "posts" table in your database.
 *
 * Example post:
 * {
 *   id: 1,
 *   content: "I finally understand recursion!",
 *   user: { id: 1, name: "Hafsa" },
 *   createdAt: "2026-06-25T10:30:00",
 *   helpfulCount: 5
 * }
 */
@Entity
@Table(name = "posts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    /*
     * Primary key - unique ID for each post
     * Database auto-generates: 1, 2, 3, 4...
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * The actual content/text of the post
     *
     * @Column(columnDefinition = "TEXT") means:
     * - Use TEXT type in database (allows long content)
     * - Default VARCHAR only allows 255 characters
     *
     * nullable = false means content is required
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /*
     * WHO wrote this post - links to User entity
     *
     * @ManyToOne means: Many posts can belong to ONE user
     * Think: One user writes many posts
     *
     * @JoinColumn(name = "user_id") means:
     * - Create a column called "user_id" in posts table
     * - This column stores the ID of the user who wrote it
     *
     * Example in database:
     * posts table:
     * | id | content              | user_id |
     * | 1  | "I learned recursion"| 1       |  ← user_id links to users table
     */
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /*
     * When the post was created
     *
     * LocalDateTime stores date + time: 2026-06-25T10:30:00
     */
    private LocalDateTime createdAt;

    /*
     * How many people found this post helpful
     * Starts at 0
     */
    private int helpfulCount = 0;

    /*
     * Anonymous posting option
     *
     * If true: hide user's name, show "Anonymous" instead
     * This lets users ask "silly" questions without embarrassment
     *
     * Default is false (normal post showing username)
     */
    private boolean anonymous = false;

    /*
     * Topics this post belongs to. Examples: "Programming", "Mathematics".
     *
     * WHY THIS IS A COLLECTION AND NOT A STRING
     *
     * This used to be a single column holding "Programming, Web Development".
     * That looked fine but made the feed impossible to query: matching a user
     * whose interest is "Programming" meant asking the database whether one
     * string contains another, and the personalized feed simply never matched
     * anything.
     *
     * Storing a list inside one column is a denormalized design. The fix is to
     * normalize it - give each topic its own row:
     *
     *   post_topics
     *   | post_id | topic              |
     *   | 1       | Programming        |
     *   | 1       | Web Development    |
     *   | 2       | Mathematics        |
     *
     * Now "find posts about Programming" is an ordinary indexed lookup, and a
     * post can have any number of topics without changing the schema.
     *
     * @ElementCollection creates that side table for a collection of plain
     * values (as opposed to @OneToMany, which is for a collection of entities).
     *
     * Topics are stored in their display form ("Web Development") and compared
     * case-insensitively in queries, so the UI stays readable.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "post_topics",
            joinColumns = @JoinColumn(name = "post_id")
    )
    @Column(name = "topic", nullable = false)
    private Set<String> topics = new LinkedHashSet<>();

    /*
     * @PrePersist runs BEFORE saving to database
     * Automatically sets createdAt to current time
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

}
