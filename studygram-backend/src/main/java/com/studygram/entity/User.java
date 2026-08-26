package com.studygram.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String username;

    // User can login with email OR phone
    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String phoneNumber;

    @Column(nullable = false)
    private String password;

    // school, college, university, other
    private String education;

    // Comma-separated: "math,programming,business"
    private String interests;

    private String careerGoal;

    // Optional
    private String githubUsername;

    /*
     * PRIVACY SETTINGS
     *
     * Which fields are withheld from other people. Enforced on the way out, in
     * UserProfileResponse - a hidden field is never written into the response
     * at all, so there is nothing to dig out of the JSON.
     *
     * PRIVATE BY DEFAULT FOR CONTACT DETAILS
     *
     * Email and phone default to hidden; everything else defaults to visible.
     *
     * These used to all default to false, with seven switches on the profile
     * page for changing them. Nobody configures seven switches - so in practice
     * everyone's email address and phone number were public, because that was
     * the default and nothing prompted them to think about it.
     *
     * A safe default beats a setting. The switches are gone from the UI; the
     * flags and their enforcement stay, because the rule is still worth having
     * and the API still honours whatever is stored here.
     */
    private boolean hideName = false;
    private boolean hideEmail = true;
    private boolean hidePhone = true;
    private boolean hideEducation = false;
    private boolean hideInterests = false;
    private boolean hideCareerGoal = false;
    private boolean hideGithub = false;

}
