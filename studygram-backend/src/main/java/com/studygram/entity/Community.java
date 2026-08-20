package com.studygram.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/*
 * Community Entity - A group for a specific interest/topic
 *
 * Examples:
 *   - Math Community
 *   - Science Community
 *   - Programming Community
 *   - Business Community
 *
 * How communities work:
 * 1. App has pre-defined communities (math, science, etc.)
 * 2. When user sets interests "math,science", they auto-join those communities
 * 3. Posts with topic "math" appear in Math Community
 * 4. Users see posts from communities they've joined
 */
@Entity
@Table(name = "communities")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Community {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Community name: "math", "science", "programming"
     * Lowercase to match with interests and post topics
     */
    @Column(nullable = false, unique = true)
    private String name;

    /*
     * Display name: "Math", "Science", "Programming"
     * Capitalized for showing in UI
     */
    private String displayName;

    /*
     * Description of what this community is about
     */
    private String description;

    /*
     * Broad grouping: "Technology", "Sciences", "Business", etc.
     *
     * Lets the topic picker show 65 topics as a handful of labelled sections
     * instead of one very long alphabetical list.
     */
    private String category;

}
