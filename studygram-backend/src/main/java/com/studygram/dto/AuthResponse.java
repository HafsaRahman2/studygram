package com.studygram.dto;

import lombok.Data;

/*
 * AuthResponse - What signup and login return
 *
 * Two things: the token, and the profile.
 *
 * The token is the credential. Every later request carries it back in an
 * Authorization header, and that is how the server knows who is calling.
 *
 * The profile comes along so the frontend can render a name and avatar
 * immediately, instead of logging in and then making a second request to find
 * out who it just logged in as.
 *
 * Nothing else belongs here. In particular, the password hash must never
 * appear - which is guaranteed by construction, because UserProfileResponse has
 * no field for it.
 */
@Data
public class AuthResponse {

    /*
     * The signed JWT.
     *
     * Named "token" rather than something like "sessionId" because it is not a
     * session: the server keeps no record of it. It is a self-contained,
     * signed statement of identity that expires on its own.
     */
    private String token;

    private UserProfileResponse user;

    public static AuthResponse of(String token, UserProfileResponse user) {
        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setUser(user);
        return response;
    }

}
