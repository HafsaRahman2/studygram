package com.studygram.dto;

import lombok.Data;

/*
 * ForgotPasswordRequest - Step 1 of the password reset flow
 *
 * The user only tells us WHERE to send the reset token:
 * {
 *   "email": "hafsa@email.com"
 * }
 *
 * Notice there is no password field here. Step 1 cannot change anything -
 * it only starts the process. That separation is what makes the flow safe.
 */
@Data
public class ForgotPasswordRequest {

    private String email;

}
