package com.studygram.dto;

import lombok.Data;

/*
 * ResetPasswordRequest - Step 2 of the password reset flow
 *
 * The user proves who they are by presenting the token, then sets a new
 * password:
 * {
 *   "token": "3f2b8c10-...",
 *   "newPassword": "mynewpassword"
 * }
 *
 * There is deliberately NO email or userId field. If the caller could name the
 * account, they could reset somebody else's. The token alone decides whose
 * password changes.
 */
@Data
public class ResetPasswordRequest {

    // The one-time token from the reset email (logged to the console in dev)
    private String token;

    // The new password to set
    private String newPassword;

}
