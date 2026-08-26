package com.studygram;

import com.studygram.entity.Post;
import com.studygram.entity.User;
import com.studygram.repository.PostRepository;
import com.studygram.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/*
 * Integration tests for authentication and authorization.
 *
 * These are the tests that matter most in this project, because they cover the
 * exact hole that existed before JWTs were added:
 *
 *     DELETE /api/posts/5?userId=1
 *
 * The server checked that user 1 owned post 5, but never checked that the
 * CALLER was user 1. Changing the number let you delete anybody's post. Every
 * test below named "cannot..." exists to make sure that class of bug cannot
 * come back unnoticed.
 *
 * @SpringBootTest starts the whole application - real controllers, real
 * services, real security filter chain, real (in-memory) database. MockMvc then
 * sends genuine HTTP requests through it without opening a network port.
 */
class AuthorizationIntegrationTest extends IntegrationTestBase {

    /* Two users, so "acting as somebody else" is actually testable. */
    private User alice;
    private User bob;
    private String aliceToken;
    private String bobToken;
    private Long alicePostId;

    @BeforeEach
    void setUp() throws Exception {
        alice = createUser("alice", "alice@test.com");
        bob = createUser("bob", "bob@test.com");

        aliceToken = login("alice@test.com");
        bobToken = login("bob@test.com");

        Post post = new Post();
        post.setContent("Alice's post about recursion");
        post.setTopics(Set.of("Programming"));
        post.setUser(alice);
        alicePostId = postRepository.save(post).getId();
    }

    private User createUser(String username, String email) {
        User user = new User();
        user.setName(username);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setInterests("Programming");
        return userRepository.save(user);
    }

    /* ================================================================
     * Getting in
     * ================================================================ */

    @Test
    @DisplayName("login returns a token and the user's profile")
    void loginReturnsToken() throws Exception {
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("emailOrPhone", "alice@test.com", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("alice"))
                // The password hash must never appear in a response.
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    @DisplayName("login with a wrong password does not say which part was wrong")
    void loginFailureIsVague() throws Exception {
        /*
         * Guards against user enumeration. If a wrong password said "incorrect
         * password" while an unknown address said "no such user", the endpoint
         * would become a tool for discovering who has an account here.
         */
        String wrongPassword = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("emailOrPhone", "alice@test.com", "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownUser = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("emailOrPhone", "nobody@test.com", "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword)
                .as("the two failures must be indistinguishable")
                .isEqualTo(unknownUser);
    }

    @Test
    @DisplayName("signup returns a token, so you are logged in straight away")
    void signupReturnsToken() throws Exception {
        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Carol",
                                "username", "carol",
                                "email", "carol@test.com",
                                "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("carol"));
    }

    /* ================================================================
     * The door is locked
     * ================================================================ */

    @Test
    @DisplayName("a protected endpoint refuses a request with no token")
    void noTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a protected endpoint refuses a made-up token")
    void invalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/posts").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a valid token gets in")
    void validTokenIsAccepted() throws Exception {
        mockMvc.perform(get("/api/posts").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("public endpoints stay reachable without a token")
    void publicEndpointsAreOpen() throws Exception {
        // You cannot log in if logging in requires being logged in.
        mockMvc.perform(get("/api/hello")).andExpect(status().isOk());

        mockMvc.perform(post("/api/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "alice@test.com"))))
                .andExpect(status().isOk());
    }

    /* ================================================================
     * You cannot act as somebody else
     *
     * Each of these was possible before JWTs were introduced.
     * ================================================================ */

    @Test
    @DisplayName("cannot delete another user's post")
    void cannotDeleteAnotherUsersPost() throws Exception {
        /*
         * THE ORIGINAL BUG.
         *
         * Bob sends a delete for Alice's post. Under the old design he would
         * add ?userId=1 and the server would take his word for it. Now his
         * token says he is Bob, the ownership check runs against Bob, and it
         * fails.
         */
        mockMvc.perform(delete("/api/posts/" + alicePostId)
                        .header("Authorization", bearer(bobToken)))
                .andExpect(status().isBadRequest());

        assertThat(postRepository.findById(alicePostId))
                .as("Alice's post must still exist")
                .isPresent();
    }

    @Test
    @DisplayName("the owner CAN delete their own post")
    void ownerCanDeleteOwnPost() throws Exception {
        mockMvc.perform(delete("/api/posts/" + alicePostId)
                        .header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk());

        assertThat(postRepository.findById(alicePostId)).isEmpty();
    }

    @Test
    @DisplayName("cannot post as another user by putting their id in the body")
    void cannotPostAsAnotherUser() throws Exception {
        /*
         * Bob writes a post and tries to attribute it to Alice by including her
         * id. The DTO no longer has a userId field at all, so the value is
         * simply dropped and the author comes from his token.
         */
        String response = mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", alice.getId(),   // ignored
                                "content", "Bob pretending to be Alice",
                                "topics", java.util.List.of("Programming"),
                                "anonymous", false))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(response).get("authorUsername").asText())
                .as("the author must be whoever the token says, not the body")
                .isEqualTo("bob");
    }

    @Test
    @DisplayName("cannot edit another user's profile")
    void cannotEditAnotherUsersProfile() throws Exception {
        /*
         * This endpoint used to be PUT /api/profile/{userId}. Changing the
         * number edited someone else's profile - including their privacy
         * settings. The id is gone from the URL entirely; there is nothing left
         * to change.
         */
        mockMvc.perform(put("/api/profile")
                        .header("Authorization", bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("careerGoal", "Bob was here"))))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(alice.getId()).orElseThrow().getCareerGoal())
                .as("Alice's profile must be untouched")
                .isNotEqualTo("Bob was here");

        assertThat(userRepository.findById(bob.getId()).orElseThrow().getCareerGoal())
                .as("Bob edited his own profile")
                .isEqualTo("Bob was here");
    }

    @Test
    @DisplayName("cannot change another user's password")
    void cannotChangeAnotherUsersPassword() throws Exception {
        String aliceHashBefore = userRepository.findById(alice.getId()).orElseThrow().getPassword();

        /*
         * Bob supplies Alice's id in the body and his own current password.
         * ChangePasswordRequest has no userId field any more, so the request
         * applies to Bob - and it fails only if his own current password is
         * wrong. Either way, Alice is unaffected.
         */
        mockMvc.perform(post("/api/change-password")
                        .header("Authorization", bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", alice.getId(),   // ignored
                                "currentPassword", PASSWORD,
                                "newPassword", "hacked123"))));

        assertThat(userRepository.findById(alice.getId()).orElseThrow().getPassword())
                .as("Alice's password hash must be unchanged")
                .isEqualTo(aliceHashBefore);
    }

    @Test
    @DisplayName("the personalized feed is always your own")
    void personalizedFeedIsYourOwn() throws Exception {
        /*
         * Was GET /api/posts/feed/{userId}, so you could read anybody's
         * personalized feed - and therefore infer their interests even if they
         * had marked interests private. There is no id in the URL now.
         */
        mockMvc.perform(get("/api/posts/feed").header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/posts/feed/" + alice.getId())
                        .header("Authorization", bearer(bobToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("break status is always your own")
    void breakStatusIsYourOwn() throws Exception {
        mockMvc.perform(get("/api/breaks/status").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AVAILABLE"));

        // The old per-user URL no longer exists.
        mockMvc.perform(get("/api/breaks/status/" + alice.getId())
                        .header("Authorization", bearer(bobToken)))
                .andExpect(status().isNotFound());
    }

    /* ================================================================
     * Privacy is enforced by the server
     * ================================================================ */

    @Test
    @DisplayName("hidden profile fields are absent from another user's view")
    void hiddenFieldsAreNotSent() throws Exception {
        alice.setHideEmail(true);
        alice.setHideCareerGoal(true);
        alice.setCareerGoal("Secret ambition");
        userRepository.save(alice);

        // Bob looks at Alice's profile: the hidden fields are simply not there.
        mockMvc.perform(get("/api/profile/alice").header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.careerGoal").doesNotExist());

        // Alice looks at her own profile: she sees everything.
        mockMvc.perform(get("/api/profile/alice").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@test.com"))
                .andExpect(jsonPath("$.careerGoal").value("Secret ambition"));
    }

    @Test
    @DisplayName("an anonymous post sends nothing that identifies its author")
    void anonymousPostHidesAuthorCompletely() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "An embarrassing question",
                                "topics", java.util.List.of("Programming"),
                                "anonymous", true))))
                .andExpect(status().isOk());

        /*
         * Bob reads the feed. The anonymous post must carry no author id and no
         * username - it is not enough to display "Anonymous" while shipping the
         * real id in the JSON underneath, which is what the first version did.
         */
        String feed = mockMvc.perform(get("/api/posts").header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var anonymousPosts = objectMapper.readTree(feed).findValues("anonymous");
        assertThat(anonymousPosts).isNotEmpty();

        objectMapper.readTree(feed).forEach(node -> {
            if (node.get("anonymous").asBoolean()) {
                assertThat(node.get("authorId").isNull())
                        .as("anonymous post must not carry an author id")
                        .isTrue();
                assertThat(node.get("authorUsername").isNull())
                        .as("anonymous post must not carry a username")
                        .isTrue();
                assertThat(node.get("ownPost").asBoolean())
                        .as("Bob does not own Alice's anonymous post")
                        .isFalse();
            }
        });
    }

    @Test
    @DisplayName("you can still tell your own anonymous posts apart")
    void ownAnonymousPostIsMarkedAsYours() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "My anonymous post",
                                "topics", java.util.List.of("Programming"),
                                "anonymous", true))))
                .andExpect(status().isOk());

        /*
         * The point of `ownPost`: Alice needs a Delete button on her own
         * anonymous post, and getting one must not require the server to reveal
         * who wrote it.
         */
        String feed = mockMvc.perform(get("/api/posts").header("Authorization", bearer(aliceToken)))
                .andReturn().getResponse().getContentAsString();

        objectMapper.readTree(feed).forEach(node -> {
            if (node.get("anonymous").asBoolean()) {
                assertThat(node.get("ownPost").asBoolean()).isTrue();
                assertThat(node.get("authorId").isNull())
                        .as("still no author id, even for the owner")
                        .isTrue();
            }
        });
    }

}
