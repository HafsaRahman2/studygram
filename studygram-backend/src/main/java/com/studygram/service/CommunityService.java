package com.studygram.service;

import com.studygram.entity.Community;
import com.studygram.entity.Post;
import com.studygram.entity.User;
import com.studygram.repository.CommunityRepository;
import com.studygram.repository.PostRepository;
import com.studygram.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/*
 * CommunityService - Business logic for Communities
 *
 * Handles:
 *   - Getting all communities
 *   - Getting user's communities (based on interests)
 *   - Getting posts in a community
 *   - Getting member count for a community
 */
@Service
public class CommunityService {

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    /*
     * GET ALL COMMUNITIES
     * Returns all available communities in the app
     */
    public List<Community> getAllCommunities() {
        return communityRepository.findAll();
    }

    /*
     * GET USER'S COMMUNITIES
     * Returns communities matching user's interests
     *
     * Example:
     *   User interests: "math,science,programming"
     *   Returns: Math, Science, Programming communities
     */
    public List<Community> getUserCommunities(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String interests = user.getInterests();

        if (interests == null || interests.isEmpty()) {
            return List.of();  // No interests = no communities
        }

        // Split "math,science" into ["math", "science"]
        List<String> interestList = Arrays.asList(interests.split(","));

        return communityRepository.findByNameIn(interestList);
    }

    /*
     * GET COMMUNITY BY NAME
     */
    public Community getCommunityByName(String name) {
        return communityRepository.findByName(name.toLowerCase())
                .orElseThrow(() -> new RuntimeException("Community not found"));
    }

    /*
     * GET POSTS IN A COMMUNITY
     * Returns all posts with matching topic
     */
    public List<Post> getCommunityPosts(String communityName) {

        // Verify community exists
        communityRepository.findByName(communityName.toLowerCase())
                .orElseThrow(() -> new RuntimeException("Community not found"));

        // Find posts tagged with this community's topic
        return postRepository.findByTopicsIn(
                List.of(communityName.toLowerCase())
        );
    }

    /*
     * CREATE COMMUNITY
     * Admin function to add new communities
     */
    public Community createCommunity(Community community) {

        // Ensure name is lowercase
        community.setName(community.getName().toLowerCase());

        if (communityRepository.existsByName(community.getName())) {
            throw new RuntimeException("Community already exists");
        }

        return communityRepository.save(community);
    }

}
