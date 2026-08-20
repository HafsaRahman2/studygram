package com.studygram.dto;

import lombok.Data;

/*
 * CreateCommentRequest - Data needed to create a comment
 *
 * Frontend sends:
 * {
 *   "userId": 1,
 *   "postId": 5,
 *   "content": "Great explanation!",
 *   "anonymous": false
 * }
 */
@Data
public class CreateCommentRequest {

    // Who is commenting
    private Long userId;

    // Which post to comment on
    private Long postId;

    // The comment text
    private String content;

    // Hide identity?
    private boolean anonymous;

}
