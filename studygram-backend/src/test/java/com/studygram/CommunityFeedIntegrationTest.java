package com.studygram;

import com.studygram.entity.Community;
import com.studygram.entity.Post;
import com.studygram.entity.User;
import com.studygram.repository.CommunityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/*
 * The same post, reached two different ways, must look the same.
 *
 * THE BUG THESE TESTS EXIST FOR
 *
 * There were two ways to reach a post: the feed (/api/posts) and browsing a
 * topic (/api/communities/{name}/posts). The feed built its responses through
 * PostService.toResponses, which attaches comment counts and helpful marks in a
 * fixed number of queries and knows who is looking. Topic browsing did not - it
 * mapped with the bare PostResponse.fromPost, which fills in a comment count of
 * zero, no helpful marks, and no viewer.
 *
 * So one post genuinely reported "1 answer" in the feed and "0 answers" under
 * its own topic, questions never opened their answer threads when browsed by
 * topic, and your own posts lost their Delete and Mark-resolved buttons because
 * `ownPost` was computed against a viewer of null.
 *
 * Nothing failed. Nothing was logged. The data was simply wrong on one of the
 * two routes, which is why it lasted as long as it did - and why the assertions
 * below compare the two routes against each other rather than against
 * hand-written expectations. A future third route that forgets toResponses
 * fails here rather than shipping quietly.
 */
class CommunityFeedIntegrationTest extends IntegrationTestBase {

    @Autowired
    private CommunityRepository communityRepository;

    private User alice;
    private User bob;
    private String aliceToken;
    private Long questionId;

    private static final String TOPIC = "Programming";

    @BeforeEach
    void setUp() throws Exception {
        alice = createUser("alice", "alice@test.com");
        bob = createUser("bob", "bob@test.com");
        aliceToken = login("alice@test.com");

        /*
         * getCommunityPosts refuses to serve a topic that does not exist, and
         * the seeder is a CommandLineRunner - which @SpringBootTest does not
         * run. So the community has to be created here.
         *
         * findByName-or-create rather than a bare save: the context is shared
         * across the whole suite, so this may already exist from an earlier
         * class and a second insert would break the unique name constraint.
         */
        communityRepository.findByName(TOPIC.toLowerCase()).orElseGet(() -> {
            Community community = new Community();
            community.setName(TOPIC.toLowerCase());
            community.setDisplayName(TOPIC);
            community.setCategory("Technology");
            return communityRepository.save(community);
        });

        Post question = new Post();
        question.setContent("Why do we need a base case in recursion?");
        question.setTopics(Set.of(TOPIC));
        question.setPostType(Post.TYPE_QUESTION);
        question.setUser(alice);
        questionId = postRepository.save(question).getId();

        /* One answer, so a comment count of zero is provably wrong. */
        String bobToken = login("bob@test.com");
        mockMvc.perform(post("/api/comments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "postId", questionId,
                                "content", "Without one it never stops calling itself.",
                                "anonymous", false))))
                .andExpect(status().isOk());
    }

    private User createUser(String username, String email) {
        User user = new User();
        user.setName(username);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setInterests(TOPIC);
        return userRepository.save(user);
    }

    /* Fetch one post from a list endpoint, as JSON. */
    private com.fasterxml.jackson.databind.JsonNode fetchPost(String url) throws Exception {
        String body = mockMvc.perform(get(url)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (com.fasterxml.jackson.databind.JsonNode node : objectMapper.readTree(body)) {
            if (node.get("id").asLong() == questionId) return node;
        }

        throw new AssertionError("Post " + questionId + " was not returned by " + url);
    }

    @Test
    @DisplayName("browsing a topic reports the same comment count as the feed")
    void topicFeedHasCommentCounts() throws Exception {
        var fromFeed = fetchPost("/api/posts");
        var fromTopic = fetchPost("/api/communities/programming/posts");

        /* The real answer count, so neither route can be trivially right. */
        assertThat(fromFeed.get("commentCount").asInt()).isEqualTo(1);

        assertThat(fromTopic.get("commentCount").asInt())
                .as("a post's answer count must not depend on how you reached it")
                .isEqualTo(fromFeed.get("commentCount").asInt());
    }

    @Test
    @DisplayName("browsing a topic still knows the post is yours")
    void topicFeedKnowsTheViewer() throws Exception {
        var fromFeed = fetchPost("/api/posts");
        var fromTopic = fetchPost("/api/communities/programming/posts");

        /*
         * ownPost is what shows Alice her Delete and Mark-resolved buttons.
         * Computed without a viewer it is always false, and the controls simply
         * were not there when she reached her own question through its topic.
         */
        assertThat(fromFeed.get("ownPost").asBoolean()).isTrue();

        assertThat(fromTopic.get("ownPost").asBoolean())
                .as("ownPost must not depend on how you reached the post")
                .isTrue();
    }

    @Test
    @DisplayName("browsing a topic reports who marked the post helpful")
    void topicFeedHasHelpfulMarks() throws Exception {
        String bobToken = login("bob@test.com");
        mockMvc.perform(post("/api/posts/" + questionId + "/helpful")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isOk());

        var fromTopic = fetchPost("/api/communities/programming/posts");

        /*
         * helpfulUsers is what draws the button's pressed state. Left empty, the
         * mark looked un-set to the person who had just made it, and pressing it
         * again removed a mark they could not see.
         */
        assertThat(fromTopic.get("helpfulCount").asInt()).isEqualTo(1);
        assertThat(fromTopic.get("helpfulUsers"))
                .as("the topic route must report helpful marks, not an empty list")
                .hasSize(1);
        assertThat(fromTopic.get("helpfulUsers").get(0).asText()).isEqualTo("bob");
    }

    @Test
    @DisplayName("a topic name with a space is reachable")
    void multiWordTopicIsReachable() throws Exception {
        communityRepository.findByName("web development").orElseGet(() -> {
            Community community = new Community();
            community.setName("web development");
            community.setDisplayName("Web Development");
            community.setCategory("Technology");
            return communityRepository.save(community);
        });

        /*
         * Topic names are words, not slugs, so they need escaping on the way
         * into a URL. This is the cheap half of that lesson; the expensive half
         * was "UI/UX Design", whose slash is a path separator no amount of
         * escaping survives - it got renamed to UX Design instead.
         */
        /*
         * get(URI) rather than get(String): the String form treats its argument
         * as a template and encodes it again, turning %20 into %2520. A real
         * browser sends what is written here.
         */
        mockMvc.perform(get(URI.create("/api/communities/web%20development/posts"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("no seeded topic name can break a URL")
    void seededTopicNamesAreUrlSafe() {
        /*
         * The seeder derives a community's name from its display name, and that
         * name goes straight into a path. A '/', '?' or '#' in one silently
         * makes that topic unreachable - which is exactly what happened, to
         * exactly one topic out of sixty-five, unnoticed.
         */
        assertThat(communityRepository.findAll())
                .extracting(Community::getName)
                .as("a topic name containing a URL delimiter cannot be fetched")
                .allSatisfy(name ->
                        assertThat(name).doesNotContain("/").doesNotContain("?").doesNotContain("#"));
    }

}
