package com.studygram.service;

import com.studygram.entity.PasswordResetToken;
import com.studygram.entity.User;
import com.studygram.repository.PasswordResetTokenRepository;
import com.studygram.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/*
 * UserService - Contains all business logic for User operations
 *
 * @Service tells Spring: "This is a service class, manage it for me"
 * Spring creates one instance and reuses it (called a "bean")
 */
@Service
public class UserService {

    /*
     * @Autowired tells Spring: "Inject the UserRepository here"
     * Spring automatically connects the repository to this service
     * You don't need to create it yourself with "new UserRepository()"
     */
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    /*
     * A logger instead of System.out.println.
     * Real applications need timestamps, levels (INFO/WARN/ERROR) and the
     * ability to route output to files - println gives you none of that.
     */
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /*
     * How long a password reset token stays valid.
     */
    private static final int RESET_TOKEN_MINUTES = 30;

    /*
     * BCryptPasswordEncoder - hashes passwords for security
     * Never store plain text passwords! "password123" becomes
     * "$2a$10$N9qo8uLOickgx2ZMRZoMy..." (impossible to reverse)
     */
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /*
     * SIGNUP - Register a new user
     *
     * Steps:
     * 1. Check if username already taken
     * 2. Check if email already taken (if provided)
     * 3. Check if phone already taken (if provided)
     * 4. Hash the password
     * 5. Save to database
     */
    public User registerUser(User user) {

        // Validation: username must be unique
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already taken");
        }

        // Validation: email must be unique (if provided)
        if (user.getEmail() != null && userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Validation: phone must be unique (if provided)
        if (user.getPhoneNumber() != null && userRepository.existsByPhoneNumber(user.getPhoneNumber())) {
            throw new RuntimeException("Phone number already registered");
        }

        // Security: hash the password before saving
        String hashedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(hashedPassword);

        // Save user to database and return the saved user
        return userRepository.save(user);
    }

    /*
     * LOGIN - Authenticate user with email/phone and password
     *
     * Steps:
     * 1. Find user by email or phone
     * 2. Compare provided password with stored hash
     * 3. Return user if match, throw error if not
     */
    public User login(String emailOrPhone, String password) {

        // Try to find user by email first, then by phone
        Optional<User> userOptional = userRepository.findByEmail(emailOrPhone);

        if (userOptional.isEmpty()) {
            userOptional = userRepository.findByPhoneNumber(emailOrPhone);
        }

        // If user not found with email or phone
        if (userOptional.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        User user = userOptional.get();

        // Compare password: passwordEncoder.matches(plainText, hashedPassword)
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        return user;
    }

    /*
     * GET USER BY USERNAME - For viewing profiles
     */
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /*
     * GET USER BY ID - For getting current user info
     */
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /*
     * UPDATE PROFILE - Update user's optional info and privacy settings
     *
     * Only updates fields that are provided (not null)
     */
    public User updateProfile(Long userId, String name, String education, String interests,
                              String careerGoal, String githubUsername,
                              Boolean hideName, Boolean hideEmail, Boolean hidePhone,
                              Boolean hideEducation, Boolean hideInterests,
                              Boolean hideCareerGoal, Boolean hideGithub) {

        User user = getUserById(userId);

        // Update profile info if provided
        if (name != null) user.setName(name);
        if (education != null) user.setEducation(education);
        if (interests != null) user.setInterests(interests);
        if (careerGoal != null) user.setCareerGoal(careerGoal);
        if (githubUsername != null) user.setGithubUsername(githubUsername);

        // Update privacy settings if provided
        if (hideName != null) user.setHideName(hideName);
        if (hideEmail != null) user.setHideEmail(hideEmail);
        if (hidePhone != null) user.setHidePhone(hidePhone);
        if (hideEducation != null) user.setHideEducation(hideEducation);
        if (hideInterests != null) user.setHideInterests(hideInterests);
        if (hideCareerGoal != null) user.setHideCareerGoal(hideCareerGoal);
        if (hideGithub != null) user.setHideGithub(hideGithub);

        return userRepository.save(user);
    }

    /*
     * CHANGE PASSWORD - User changes their own password while logged in
     *
     * Requires current password for security verification
     */
    public void changePassword(Long userId, String currentPassword, String newPassword) {

        User user = getUserById(userId);

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        // Hash and set new password
        String hashedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(hashedPassword);

        userRepository.save(user);
    }

    /*
     * STEP 1 OF PASSWORD RESET - Request a reset token
     *
     * SECURITY NOTE - USER ENUMERATION
     *
     * This method deliberately does NOT tell the caller whether the email
     * exists. If it threw "No account found with this email", an attacker could
     * feed it a list of addresses and learn exactly which people have accounts
     * here. That leak is called "user enumeration".
     *
     * So: unknown email -> we do nothing, quietly, and the controller returns
     * the same cheerful message it returns for a real account.
     *
     * In a production app the token would be emailed to the user. This project
     * has no mail server, so the token is written to the backend log instead -
     * the same pattern Django and Rails use in development. Whoever runs the
     * server can read it from the console.
     */
    @Transactional
    public void requestPasswordReset(String email) {

        Optional<User> userOptional = userRepository.findByEmail(email);

        // Unknown email: do nothing at all, and reveal nothing.
        if (userOptional.isEmpty()) {
            log.info("Password reset requested for an email with no account. Ignoring.");
            return;
        }

        User user = userOptional.get();

        // Requesting a new link kills any older outstanding links.
        resetTokenRepository.invalidateAllForUser(user);

        // UUID.randomUUID() is cryptographically random - not guessable.
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_MINUTES));

        resetTokenRepository.save(token);

        // Stands in for sending an email.
        log.info("""

                =====================================================
                 PASSWORD RESET TOKEN for {}
                 {}
                 Valid for {} minutes.
                 (In production this would be emailed, not logged.)
                =====================================================
                """, user.getUsername(), token.getToken(), RESET_TOKEN_MINUTES);
    }

    /*
     * STEP 2 OF PASSWORD RESET - Redeem the token and set a new password
     *
     * Note what identifies the account here: the TOKEN, and nothing else.
     * The caller never gets to say whose password to change.
     */
    @Transactional
    public void resetPasswordWithToken(String token, String newPassword) {

        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("Password must be at least 6 characters");
        }

        PasswordResetToken resetToken = resetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

        // isValid() covers both "already used" and "expired".
        // The error message is deliberately the same for every failure reason so
        // an attacker cannot tell a real-but-expired token from a fake one.
        if (!resetToken.isValid()) {
            throw new RuntimeException("Invalid or expired reset token");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Burn the token so it can never be replayed.
        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);

        log.info("Password successfully reset for user {}", user.getUsername());
    }

}
