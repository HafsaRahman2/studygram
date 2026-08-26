package com.studygram.dto;

import lombok.Data;

/*
 * CreateCommentRequest - Data needed to create a comment
 *
 * Frontend sends:
 * {
 *   "postId": 5,
 *   "content": "Great explanation!",
 *   "anonymous": false
 * }
 *
 * There is no userId field. Who is commenting is taken from the token on the
 * request, not from anything the client says about itself.
 */
@Data
public class CreateCommentRequest {

    // Which post to comment on
    private Long postId;

    // The comment text
    private String content;

    // Hide identity?
    private boolean anonymous;

}
