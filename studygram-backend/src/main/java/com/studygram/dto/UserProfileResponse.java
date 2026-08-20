package com.studygram.dto;

import com.studygram.entity.User;
import lombok.Data;

/*
 * UserProfileResponse - A user's profile as seen by somebody else
 *
 * WHY THIS CLASS EXISTS
 *
 * The User entity has seven "hideX" privacy flags. Before this class, those
 * flags were saved to the database and then ignored: /api/profile/{username}
 * returned the whole User object - email, phone number, everything - and the
 * React app just chose not to draw the hidden fields.
 *
 * That is not privacy. Anyone could open DevTools, or curl the endpoint, and
 * read every "hidden" field in the raw JSON.
 *
 * Privacy has to be enforced where the data leaves the server. So this DTO
 * builds the response field by field, and a hidden field is simply never
 * populated - it goes out as null, because there is nothing there to find.
 *
 * The one exception is you looking at your own profile, where you obviously
 * should see your own hidden fields (and the flags themselves, so the settings
 * screen can show which switches are on).
 */
@Data
public class UserProfileResponse {

    // Always visible - a username is how you are addressed here
    private Long id;
    private String username;

    // Visible unless hidden
    private String name;
    private String email;
    private String phoneNumber;
    private String education;
    private String interests;
    private String careerGoal;
    private String githubUsername;

    /*
     * Tells the frontend a field was deliberately hidden, so it can render
     * "(Hidden)" rather than "Not set". Those mean different things to a reader
     * and it costs nothing to be honest about which is which.
     */
    private boolean hideName;
    private boolean hideEmail;
    private boolean hidePhone;
    private boolean hideEducation;
    private boolean hideInterests;
    private boolean hideCareerGoal;
    private boolean hideGithub;

    /* True when you are looking at your own profile. */
    private boolean ownProfile;

    /*
     * Build the profile a particular viewer is allowed to see.
     *
     * @param user     the profile being viewed
     * @param viewerId who is looking (may be null for a logged-out visitor)
     */
    public static UserProfileResponse of(User user, Long viewerId) {

        UserProfileResponse response = new UserProfileResponse();

        boolean isOwner = viewerId != null && viewerId.equals(user.getId());

        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setOwnProfile(isOwner);

        // The flags themselves are not secret - saying "this person hides their
        // email" reveals nothing beyond what the blank field already shows.
        response.setHideName(user.isHideName());
        response.setHideEmail(user.isHideEmail());
        response.setHidePhone(user.isHidePhone());
        response.setHideEducation(user.isHideEducation());
        response.setHideInterests(user.isHideInterests());
        response.setHideCareerGoal(user.isHideCareerGoal());
        response.setHideGithub(user.isHideGithub());

        // Each field is included only if the owner is looking, or it is not hidden.
        if (isOwner || !user.isHideName())       response.setName(user.getName());
        if (isOwner || !user.isHideEmail())      response.setEmail(user.getEmail());
        if (isOwner || !user.isHidePhone())      response.setPhoneNumber(user.getPhoneNumber());
        if (isOwner || !user.isHideEducation())  response.setEducation(user.getEducation());
        if (isOwner || !user.isHideInterests())  response.setInterests(user.getInterests());
        if (isOwner || !user.isHideCareerGoal()) response.setCareerGoal(user.getCareerGoal());
        if (isOwner || !user.isHideGithub())     response.setGithubUsername(user.getGithubUsername());

        return response;
    }

    /*
     * Build the full profile for the account's owner.
     * Used right after login and after a profile update, where the response
     * goes back to the person it belongs to.
     */
    public static UserProfileResponse ofOwner(User user) {
        return of(user, user.getId());
    }

}
