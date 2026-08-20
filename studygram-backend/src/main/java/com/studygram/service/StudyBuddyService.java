package com.studygram.service;

import com.studygram.entity.StudyBuddy;
import com.studygram.entity.User;
import com.studygram.repository.StudyBuddyRepository;
import com.studygram.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
     */
    public void rejectRequest(Long requestId, Long userId) {

        StudyBuddy request = studyBuddyRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        // Only the recipient can reject
        if (!request.getBuddy().getId().equals(userId)) {
            throw new RuntimeException("You can only reject requests sent to you");
        }

        request.setStatus("REJECTED");
        studyBuddyRepository.save(request);
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
