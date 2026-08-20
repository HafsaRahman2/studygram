package com.studygram.controller;

import com.studygram.dto.ChangePasswordRequest;
import com.studygram.dto.ForgotPasswordRequest;
import com.studygram.dto.LoginRequest;
import com.studygram.dto.ResetPasswordRequest;
import com.studygram.dto.UpdateProfileRequest;
import com.studygram.dto.UserProfileResponse;
import com.studygram.entity.User;
import com.studygram.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/*
 * UserController - Handles all HTTP requests for User operations
 *
 * @RestController = This class handles API requests and returns JSON
 * @RequestMapping("/api") = All endpoints in this class start with /api
 */
@RestController
@RequestMapping("/api")
public class UserController {

    /*
     * Inject the UserService
     * Controller calls Service, Service calls Repository
     */
    @Autowired
    private UserService userService;

    /*
     * SIGNUP ENDPOINT
     *
     * URL: POST /api/signup
     *
     * @PostMapping = This method handles POST requests
     * @RequestBody = Get JSON data from request body and convert to User object
     *
     * Example request:
     * POST /api/signup
     * {
     *   "name": "Hafsa",
     *   "username": "hafsa123",
     *   "email": "hafsa@email.com",
     *   "password": "mypassword",
     *   "education": "university",
     *   "interests": "programming,math",
     *   "careerGoal": "Software Engineer"
     * }
     *
     * ResponseEntity = wrapper that includes HTTP status code
     */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody User user) {
        try {
            // Call service to register user
            User savedUser = userService.registerUser(user);

            /*
             * Return a DTO rather than the entity. The old code sent the User
             * object back with password set to null - which works, but relies
             * on remembering to blank out every sensitive field, every time, on
             * every endpoint. A DTO cannot forget: fields that are not on the
             * class cannot be serialized.
             */
            return ResponseEntity.ok(UserProfileResponse.ofOwner(savedUser));

        } catch (RuntimeException e) {
            // Return 400 Bad Request with error message
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /*
     * LOGIN ENDPOINT
     *
     * URL: POST /api/login
     *
     * Example request:
     * POST /api/login
     * {
     *   "emailOrPhone": "hafsa@email.com",
     *   "password": "mypassword"
     * }
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            // Call service to authenticate
            User user = userService.login(
                loginRequest.getEmailOrPhone(),
                loginRequest.getPassword()
            );

            // You are logging into your own account, so you get the full view
            return ResponseEntity.ok(UserProfileResponse.ofOwner(user));

        } catch (RuntimeException e) {
            /*
             * Deliberately vague: the service distinguishes "user not found"
             * from "wrong password", but telling the caller which one it was
             * would let an attacker discover which emails have accounts here.
             */
            return ResponseEntity.status(401).body("Incorrect email/phone or password");
        }
    }

    /*
     * GET PROFILE ENDPOINT
     *
     * URL: GET /api/profile/{username}
     *
     * @GetMapping = This method handles GET requests
     * @PathVariable = Get value from the URL path
     *
     * Example: GET /api/profile/hafsa123
     * The {username} becomes "hafsa123"
     */
    @GetMapping("/profile/{username}")
    public ResponseEntity<?> getProfile(
            @PathVariable String username,
            @RequestParam(required = false) Long viewerId) {
        try {
            User user = userService.getUserByUsername(username);

            /*
             * The privacy flags are applied HERE, on the way out. A field the
             * user chose to hide is never put into the response at all, so
             * there is nothing for a curious viewer to dig out of the JSON.
             */
            return ResponseEntity.ok(UserProfileResponse.of(user, viewerId));

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * UPDATE PROFILE ENDPOINT
     *
     * URL: PUT /api/profile/{userId}
     *
     * Example request:
     * PUT /api/profile/1
     * {
     *   "education": "university",
     *   "interests": "programming,math",
     *   "careerGoal": "Software Engineer",
     *   "hideEmail": true
     * }
     *
     * Updates only the fields that are provided.
     */
    @PutMapping("/profile/{userId}")
    public ResponseEntity<?> updateProfile(
            @PathVariable Long userId,
            @RequestBody UpdateProfileRequest request) {
        try {
            User user = userService.updateProfile(
                userId,
                request.getName(),
                request.getEducation(),
                request.getInterests(),
                request.getCareerGoal(),
                request.getGithubUsername(),
                request.getHideName(),
                request.getHideEmail(),
                request.getHidePhone(),
                request.getHideEducation(),
                request.getHideInterests(),
                request.getHideCareerGoal(),
                request.getHideGithub()
            );

            // You just edited your own profile, so you get the full view back
            return ResponseEntity.ok(UserProfileResponse.ofOwner(user));

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * CHANGE PASSWORD ENDPOINT
     *
     * URL: POST /api/change-password
     *
     * For logged-in users to change their password
     * Requires current password for verification
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            userService.changePassword(
                request.getUserId(),
                request.getCurrentPassword(),
                request.getNewPassword()
            );

            return ResponseEntity.ok("Password changed successfully!");

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /*
     * FORGOT PASSWORD - STEP 1: request a reset token
     *
     * URL: POST /api/forgot-password
     * {
     *   "email": "hafsa@email.com"
     * }
     *
     * ALWAYS returns the same success message, whether or not that email has an
     * account. If it said "no account found", an attacker could use this
     * endpoint to discover which email addresses are registered here.
     *
     * The token itself is written to the backend console (see UserService).
     * A production version would email it.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {

        userService.requestPasswordReset(request.getEmail());

        return ResponseEntity.ok(
            "If an account exists for that email, a reset token has been sent."
        );
    }

    /*
     * FORGOT PASSWORD - STEP 2: redeem the token
     *
     * URL: POST /api/reset-password
     * {
     *   "token": "3f2b8c10-...",
     *   "newPassword": "mynewpassword123"
     * }
     *
     * The token proves the caller controls the account's email address.
     * It is single-use and expires after 30 minutes.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            userService.resetPasswordWithToken(
                request.getToken(),
                request.getNewPassword()
            );

            return ResponseEntity.ok("Password reset successful! You can now log in with your new password.");

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
