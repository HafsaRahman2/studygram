package com.studygram.dto;

import lombok.Data;

/*
 * LoginRequest - Data Transfer Object (DTO) for login
 *
 * DTO is a simple class that carries data between layers.
 * When user logs in, they send:
 * {
 *   "emailOrPhone": "hafsa@email.com",
 *   "password": "mypassword"
 * }
 *
 * This class holds that data.
 *
 * @Data from Lombok creates getters/setters automatically.
 */
@Data
public class LoginRequest {

    // User can login with email OR phone number
    private String emailOrPhone;

    private String password;

}
