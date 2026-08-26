package com.studygram.security;

/*
 * AuthenticatedUser - Who the current request is from
 *
 * This is what controllers receive from @AuthenticationPrincipal. It replaces
 * the old `userId` query parameter, and the difference is the entire point of
 * the change:
 *
 *   BEFORE   the client SAID who it was       (?userId=1)
 *   NOW      the server WORKED OUT who it was (verified signature on a token)
 *
 * A record rather than a class: it is immutable, carries only data, and gets
 * equals, hashCode and toString for free.
 *
 * Deliberately holds only the id and username, not the whole User entity.
 * Loading the full user from the database on every single request would be
 * wasteful, and most endpoints only need the id to answer "is this yours?".
 * Endpoints that need more can look it up themselves.
 */
public record AuthenticatedUser(Long id, String username) {
}
