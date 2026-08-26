package com.studygram.dto;

import lombok.Data;

/*
 * ChangePasswordRequest - DTO for changing your password while logged in
 *
 * There is deliberately no userId field.
 *
 * It used to have one, and the server used it - so sending {"userId": 2, ...}
 * changed user 2's password. Whose password gets changed is now decided
 * entirely by the token on the request, and the caller has no way to name a
 * different account.
 *
 * The current password is still required. The token proves you are logged in as
 * this account; the current password proves it is still YOU sitting at the
 * keyboard, and not somebody who found an unlocked laptop.
 */
@Data
public class ChangePasswordRequest {

    private String currentPassword;
    private String newPassword;

}
