package com.studygram.dto;

import com.studygram.entity.Post;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/*
 * PostResponse - What we send back to the frontend
 *
 * WHY NOT JUST SEND THE Post ENTITY?
 *
 * Because the entity knows things the browser must not see. A DTO (Data
 * Transfer Object) is a deliberate, hand-built shape for data crossing the
 * wire: whatever is not on this class cannot leak, by construction.
 *
 * Regular post:
 * {
 *   "id": 1,
 *   "content": "I learned recursion!",
 *   "authorId": 4,
 *   "authorName": "Hafsa",
 *   "authorUsername": "hafsa123",
 *   "anonymous": false,
 *   "topics": ["Programming"],
 *   "helpfulCount": 5
 * }
 *
 * Anonymous post - note authorId is null, not just the name:
 * {
 *   "id": 2,
 *   "content": "Is this a dumb question?",
 *   "authorId": null,
 *   "authorName": "Anonymous",
 *   "authorUsername": null,
 *   "anonymous": true,
 *   "topics": ["Mathematics"],
 *   "helpfulCount": 3
 * }
 */
@Data
public class PostResponse {

    private Long id;
    private String content;

    /*
     * SECURITY: this is null on anonymous posts.
     *
     * The earlier version always filled authorId in, because the frontend needs
     * it to decide whether to show a Delete button. But that defeated the whole
     * feature - anyone could open the browser's network tab, read the raw JSON,
     * and match the ID back to a user. The post said "Anonymous"; the data
     * underneath did not.
     *
     * "Hidden in the UI" is not hidden. If the browser must not know something,
     * do not send it.
     *
     * Ownership of anonymous posts is signalled by `ownPost` instead, which is
     * a yes/no answer for the one user asking, and names nobody.
     */
    private Long authorId;
    private String authorName;
    private String authorUsername;

    /*
     * True when the post belongs to the user who requested this feed.
     *
     * This lets the frontend show a Delete button on your own anonymous posts
     * without ever revealing who wrote anyone else's.
     */
    private boolean ownPost;

    private boolean anonymous;
    private LocalDateTime createdAt;

    /* Null unless the post has been edited. Drives the "edited" marker. */
    private LocalDateTime editedAt;

    private int helpfulCount;

    /*
     * Replies. Called "answers" in the UI on questions and "comments" on
     * shares - the number is the same, only the word changes.
     */
    private int commentCount;

    /* "QUESTION" or "SHARE" - decides how the card is rendered. */
    private String postType;

    /* Whether the asker has marked a question answered. */
    private boolean resolved;

    /* Every topic this post is tagged with, in display form. */
    private List<String> topics = new ArrayList<>();

    /* Usernames of people who marked this post helpful (used to highlight the button). */
    private List<String> helpfulUsers = new ArrayList<>();

    /*
     * Convert a Post entity into a PostResponse.
     *
     * @param post         the post to convert
     * @param commentCount how many comments it has
     * @param viewerId     who is asking (may be null for a logged-out viewer) -
     *                     used only to compute ownPost, never echoed back
     */
    public static PostResponse fromPost(Post post, int commentCount, Long viewerId) {

        PostResponse response = new PostResponse();

        response.setId(post.getId());
        response.setContent(post.getContent());
        response.setCreatedAt(post.getCreatedAt());
        response.setHelpfulCount(post.getHelpfulCount());
        response.setCommentCount(commentCount);
        response.setAnonymous(post.isAnonymous());
        response.setTopics(new ArrayList<>(post.getTopics()));
        response.setPostType(post.getPostType());
        response.setResolved(post.isResolved());
        response.setEditedAt(post.getEditedAt());

        Long realAuthorId = post.getUser().getId();

        // Does this post belong to the person looking at it?
        response.setOwnPost(viewerId != null && viewerId.equals(realAuthorId));

        if (post.isAnonymous()) {
            // Send NOTHING that identifies the author.
            response.setAuthorId(null);
            response.setAuthorName("Anonymous");
            response.setAuthorUsername(null);
        } else {
            response.setAuthorId(realAuthorId);
            response.setAuthorName(post.getUser().getName());
            response.setAuthorUsername(post.getUser().getUsername());
        }

        return response;
    }

    /* Convenience overload for callers with no viewer and no comment count. */
    public static PostResponse fromPost(Post post) {
        return fromPost(post, 0, null);
    }

}
