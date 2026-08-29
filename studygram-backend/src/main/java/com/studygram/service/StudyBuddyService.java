package com.studygram.service;

import com.studygram.dto.UserProfileResponse;
import com.studygram.dto.UserSearchResult;
import com.studygram.entity.StudyBuddy;
import com.studygram.entity.User;
import com.studygram.repository.StudyBuddyRepository;
import com.studygram.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * StudyBuddyService - Business logic for buddy connections
 *
 * Handles:
 *   - Sending buddy requests
 *   - Accepting/rejecting requests
 *   - Getting buddy list
 *   - Getting pending requests
 */
@Service
public class StudyBuddyService {

    @Autowired
    private StudyBuddyRepository studyBuddyRepository;

    @Autowired
    private UserRepository userRepository;

    /* Look up a user, or fail with a clear message. Used throughout. */
    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /*
     * SEND BUDDY REQUEST
     *
     * User A wants to be buddies with User B
     * Creates a PENDING request
     */
    public StudyBuddy sendBuddyRequest(Long userId, Long buddyId) {

        // Can't add yourself as buddy
        if (userId.equals(buddyId)) {
            throw new RuntimeException("You cannot add yourself as a buddy");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        User buddy = userRepository.findById(buddyId)
                .orElseThrow(() -> new RuntimeException("Buddy not found"));

        // Check if connection already exists
        if (studyBuddyRepository.findExistingConnection(user, buddy).isPresent()) {
            throw new RuntimeException("Buddy request already exists");
        }

        // Create new buddy request
        StudyBuddy request = new StudyBuddy();
        request.setUser(user);
        request.setBuddy(buddy);
        request.setStatus("PENDING");

        return studyBuddyRepository.save(request);
    }

    /*
     * ACCEPT BUDDY REQUEST
     *
     * User B accepts the request from User A
     */
    public StudyBuddy acceptRequest(Long requestId, Long userId) {

        StudyBuddy request = studyBuddyRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        // Only the recipient can accept
        if (!request.getBuddy().getId().equals(userId)) {
            throw new RuntimeException("You can only accept requests sent to you");
        }

        if (!request.getStatus().equals("PENDING")) {
            throw new RuntimeException("Request is no longer pending");
        }

        request.setStatus("ACCEPTED");
        return studyBuddyRepository.save(request);
    }

    /*
     * REJECT BUDDY REQUEST
     *
     * Declining DELETES the request. It used to set the status to "REJECTED"
     * and keep the row, which had two consequences neither of them intended.
     *
     * First, a dead end. sendBuddyRequest refuses when any connection already
     * exists, whatever its status - so one decline meant these two people could
     * never connect again, in either direction. The person who declined was
     * punished by their own click, and a misclick was permanent, with nothing
     * in the interface to undo it.
     *
     * Second, the leftover row showed to both people as "Declined". So the
     * person who was turned down got told they were turned down. The app has no
     * reason to say that.
     *
     * Deleting the row settles both: nobody is told anything, and either of
     * them can send a fresh request later if they want to. If it ever becomes a
     * way to pester somebody, the fix is a rate limit on how often you may ask
     * the same person - a rule about frequency, not a permanent wall.
     */
    public void rejectRequest(Long requestId, Long userId) {

        StudyBuddy request = studyBuddyRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        // Only the recipient can reject
        if (!request.getBuddy().getId().equals(userId)) {
            throw new RuntimeException("You can only reject requests sent to you");
        }

        studyBuddyRepository.delete(request);
    }

    /*
     * GET PENDING REQUESTS
     *
     * Returns requests waiting for user to accept/reject
     */
    public List<StudyBuddy> getPendingRequests(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return studyBuddyRepository.findByBuddyAndStatus(user, "PENDING");
    }

    /*
     * GET SENT REQUESTS
     *
     * Returns requests the user has sent that are still pending
     */
    public List<StudyBuddy> getSentRequests(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return studyBuddyRepository.findByUserAndStatus(user, "PENDING");
    }

    /*
     * GET BUDDIES LIST
     *
     * Returns all accepted buddies for a user
     */
    public List<User> getBuddies(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<StudyBuddy> connections = studyBuddyRepository.findAcceptedBuddies(user);

        // Extract the buddy from each connection
        List<User> buddies = new ArrayList<>();
        for (StudyBuddy connection : connections) {
            // Add the OTHER user (not the current user)
            if (connection.getUser().getId().equals(userId)) {
                buddies.add(connection.getBuddy());
            } else {
                buddies.add(connection.getUser());
            }
        }

        return buddies;
    }

    /*
     * GET BUDDY COUNT
     */
    public int getBuddyCount(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return studyBuddyRepository.countAcceptedBuddies(user);
    }

    /*
     * WORK OUT WHERE TWO PEOPLE STAND
     *
     * Every "find buddies" result needs a button, and which button depends on
     * this. Computing it here - once, on the server - keeps the rule in one
     * place instead of having the browser re-derive it from three separate
     * lists.
     */
    public UserSearchResult describeRelationship(User viewer, User other) {

        UserSearchResult result = new UserSearchResult();
        result.setUser(UserProfileResponse.of(other, viewer.getId()));
        result.setSharedInterests(sharedInterests(viewer, other));

        if (viewer.getId().equals(other.getId())) {
            result.setRelationship("SELF");
            return result;
        }

        StudyBuddy connection = studyBuddyRepository
                .findExistingConnection(viewer, other)
                .orElse(null);

        if (connection == null) {
            result.setRelationship("NONE");
            return result;
        }

        result.setRequestId(connection.getId());

        switch (connection.getStatus()) {
            case "ACCEPTED" -> result.setRelationship("BUDDIES");
            default -> {
                // Pending: which way round decides whether you can accept it
                boolean viewerSentIt = connection.getUser().getId().equals(viewer.getId());
                result.setRelationship(viewerSentIt ? "REQUEST_SENT" : "REQUEST_RECEIVED");
            }
        }

        return result;
    }

    /*
     * SEARCH PEOPLE by username or display name.
     *
     * Returns everyone matching, each annotated with your relationship to them.
     * A blank query returns nothing rather than everybody - "show me every user
     * on the platform" is not a search, and it is not something a social app
     * should hand out on request.
     */
    public List<UserSearchResult> searchUsers(Long viewerId, String query) {

        User viewer = findUser(viewerId);

        if (query == null || query.trim().length() < 2) {
            return List.of();
        }

        String trimmed = query.trim();

        return userRepository
                .findTop20ByUsernameContainingIgnoreCaseOrNameContainingIgnoreCase(trimmed, trimmed)
                .stream()
                // Never return the searcher to themselves
                .filter(user -> !user.getId().equals(viewerId))
                .map(user -> describeRelationship(viewer, user))
                .collect(Collectors.toList());
    }

    /*
     * SUGGEST BUDDIES - people studying the same things as you.
     *
     * This is the app's actual premise, so the ranking is the feature: sort by
     * how many interests you share, and show which ones. Someone with three
     * subjects in common is a genuinely useful suggestion; a random username is
     * not.
     *
     * People you are already connected to (or have a pending request with) are
     * filtered out - suggesting someone you asked yesterday is noise.
     *
     * SCALE NOTE: this loads every other user and ranks them in memory. At this
     * project's size that is a few dozen rows and is not worth optimising. It
     * would not survive real numbers; the fix is to normalize interests into
     * their own table, exactly as post topics were, and let the database do the
     * matching and the limiting.
     */
    public List<UserSearchResult> suggestBuddies(Long viewerId, int limit) {

        User viewer = findUser(viewerId);

        // No interests set means nothing to match on.
        if (parseInterests(viewer.getInterests()).isEmpty()) {
            return List.of();
        }

        return userRepository.findByIdNot(viewerId).stream()
                .map(candidate -> describeRelationship(viewer, candidate))
                // Only people you have no connection with, who share something
                .filter(result -> "NONE".equals(result.getRelationship()))
                .filter(result -> !result.getSharedInterests().isEmpty())
                .sorted(Comparator.comparingInt(
                        (UserSearchResult r) -> r.getSharedInterests().size()).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /*
     * Interests both people listed, in the first person's spelling.
     *
     * Compared case-insensitively, because interests are free text stored as a
     * comma-separated string: one user's "programming" and another's
     * "Programming" are the same subject and must match.
     */
    private List<String> sharedInterests(User a, User b) {

        Set<String> mine = new HashSet<>(parseInterests(a.getInterests()));

        return parseInterests(b.getInterests()).stream()
                .filter(mine::contains)
                .map(this::titleCase)
                .distinct()
                .collect(Collectors.toList());
    }

    /* "Programming, math" -> ["programming", "math"] */
    private List<String> parseInterests(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return List.of();
        }

        return Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());
    }

    /* Interests are compared lowercase but displayed capitalised. */
    private String titleCase(String value) {
        if (value.isEmpty()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /*
     * REMOVE BUDDY
     *
     * Either user can remove the connection
     */
    public void removeBuddy(Long userId, Long buddyId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        User buddy = userRepository.findById(buddyId)
                .orElseThrow(() -> new RuntimeException("Buddy not found"));

        StudyBuddy connection = studyBuddyRepository.findExistingConnection(user, buddy)
                .orElseThrow(() -> new RuntimeException("Buddy connection not found"));

        studyBuddyRepository.delete(connection);
    }

}
