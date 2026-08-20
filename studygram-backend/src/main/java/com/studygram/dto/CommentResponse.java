package com.studygram.dto;

import com.studygram.entity.Comment;
import lombok.Data;
import java.time.LocalDateTime;

/*
 * CommentResponse - What we send back for comments
 *
 * Follows the same rule as PostResponse: an anonymous comment sends no
 * identifying data at all, not even an id the client could match up.
 */
@Data
public class CommentResponse {

    private Long id;
    private String content;

    /* Null on anonymous comments - see the note in PostResponse. */
    private Long authorId;
    private String authorName;
    private String authorUsername;

    /*
     * Whether the viewer is allowed to delete this comment.
     *
     * The rule (you wrote the comment, OR you own the post it is on) lives in
     * CommentService. Computing it here as well would mean two copies of the
     * same rule drifting apart; instead the server answers the question once
     * and the UI just draws the button.
     *
     * The server re-checks on the actual DELETE regardless. This field decides
     * what to render, never what is permitted.
     */
    private boolean canDelete;

    private boolean anonymous;
    private LocalDateTime createdAt;

    /*
     * Convert a Comment entity to a CommentResponse.
     *
     * @param viewerId who is asking (may be null) - used only for canDelete
     */
    public static CommentResponse fromComment(Comment comment, Long viewerId) {

        CommentResponse response = new CommentResponse();

        response.setId(comment.getId());
        response.setContent(comment.getContent());
        response.setCreatedAt(comment.getCreatedAt());
        response.setAnonymous(comment.isAnonymous());

        Long commentOwnerId = comment.getUser().getId();
        Long postOwnerId = comment.getPost().getUser().getId();

        response.setCanDelete(
                viewerId != null
                        && (viewerId.equals(commentOwnerId) || viewerId.equals(postOwnerId))
        );

        if (comment.isAnonymous()) {
            response.setAuthorId(null);
            response.setAuthorName("Anonymous");
            response.setAuthorUsername(null);
        } else {
            response.setAuthorId(commentOwnerId);
            response.setAuthorName(comment.getUser().getName());
            response.setAuthorUsername(comment.getUser().getUsername());
        }

        return response;
    }

}
