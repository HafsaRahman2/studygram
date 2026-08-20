package com.studygram.dto;

import lombok.Data;

/*
 * UpdateProfileRequest - DTO for updating user profile
 *
 * Users can update their optional info and privacy settings.
 * All fields are optional - only update what's provided.
 */
@Data
public class UpdateProfileRequest {

    // Optional profile info
    private String name;
    private String education;
    private String interests;
    private String careerGoal;
    private String githubUsername;

    // Privacy settings (true = hidden)
    private Boolean hideName;
    private Boolean hideEmail;
    private Boolean hidePhone;
    private Boolean hideEducation;
    private Boolean hideInterests;
    private Boolean hideCareerGoal;
    private Boolean hideGithub;

}
