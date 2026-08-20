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
     * Users can choose to hide their information from others.
     * Only username is always visible.
     *
     * true = hidden (private)
     * false = visible (public) - default
     */
    private boolean hideName = false;
    private boolean hideEmail = false;
    private boolean hidePhone = false;
    private boolean hideEducation = false;
    private boolean hideInterests = false;
    private boolean hideCareerGoal = false;
    private boolean hideGithub = false;

}
