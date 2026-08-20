package com.studygram.dto;

import lombok.Data;

/*
 * ChangePasswordRequest - DTO for changing password while logged in
 *
 * User must provide current password for security
 */
@Data
public class ChangePasswordRequest {

    private Long userId;
    private String currentPassword;
    private String newPassword;

}
