package com.studygram.dto;

import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

/*
 * CreatePostRequest - Data needed to create a new post
 *
 * When a user creates a post, the frontend sends:
 * {
 *   "userId": 1,
 *   "content": "I finally understand recursion!",
 *   "topics": ["Programming", "Computer Science"],
 *   "anonymous": false
 * }
 *
 * If anonymous is true, the user's name won't show on the post.
 */
@Data
public class CreatePostRequest {

    // ID of the user creating the post
    private Long userId;

    // The text content of the post
    private String content;

    /*
     * Which topics this post is about. At least one is required - it decides
     * whose personalized feed the post shows up in.
     *
     * A Set rather than a List, because tagging the same topic twice is
     * meaningless; the Set drops the duplicate for us.
     */
    private Set<String> topics = new LinkedHashSet<>();

    // Should this post hide the user's identity?
    // true = show as "Anonymous"
    // false = show username (default)
    private boolean anonymous;

}
