package com.studygram;

import com.studygram.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/*
 * Integration tests for study buddies.
 *
 * The first test in this file is the important one: it pins down a real
 * vulnerability found while building the UI. /api/buddies/pending and /sent
 * returned StudyBuddy ENTITIES, and since each holds two User objects, the JSON
 * contained BCrypt password hashes and privacy-hidden contact details for
 * everyone in your requests list.
 *
 * The rest cover the request lifecycle and the matching logic.
 */
class StudyBuddyIntegrationTest extends IntegrationTestBase {

    private User alice;
    private User bob;
    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setUp() throws Exception {
        // Two shared interests, one each that is not shared.
        alice = createUser("alice", "Alice Adams", "alice@test.com",
                "Programming, Mathematics, Physics");
        bob = createUser("bob", "Bob Brown", "bob@test.com",
                "programming, mathematics, Cooking");

        aliceToken = login("alice@test.com");
        bobToken = login("bob@test.com");
    }

    private User createUser(String username, String name, String email, String interests) {
        User user = new User();
        user.setName(name);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setInterests(interests);
        return userRepository.save(user);
    }

    private void sendRequest(String token, Long toUserId) throws Exception {
        mockMvc.perform(post("/api/buddies/request")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("buddyId", toUserId))))
                .andExpect(status().isOk());
    }

    /* ================================================================
     * The leak
     * ================================================================ */

    @Test
    @DisplayName("THE IMPORTANT ONE: request lists never contain password hashes")
    void requestListsDoNotLeakPasswords() throws Exception {
        sendRequest(aliceToken, bob.getId());

        String sent = mockMvc.perform(get("/api/buddies/sent")
                        .header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String pending = mockMvc.perform(get("/api/buddies/pending")
                        .header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        /*
         * Checking the raw response text, not a parsed field. The bug was that
         * an entire entity got serialized, so the assertion has to be "this
         * string does not appear ANYWHERE in the payload" - a field-level check
         * would have missed it, since nobody would think to look for
         * $.[0].user.buddy.password.
         */
        assertThat(sent).doesNotContain("password").doesNotContain("$2a$");
        assertThat(pending).doesNotContain("password").doesNotContain("$2a$");
    }

    @Test
    @DisplayName("buddy lists respect the other person's privacy settings")
    void buddyListsRespectPrivacy() throws Exception {
        bob.setHideEmail(true);
        userRepository.save(bob);

        sendRequest(aliceToken, bob.getId());

        mockMvc.perform(get("/api/buddies/sent").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user.username").value("bob"))
                // Being in someone's request list does not override their privacy.
                .andExpect(jsonPath("$[0].user.email").doesNotExist());
    }

    /* ================================================================
     * The request lifecycle
     * ================================================================ */

    @Test
    @DisplayName("a request appears as OUTGOING to the sender and INCOMING to the recipient")
    void requestDirectionIsRelativeToTheViewer() throws Exception {
        sendRequest(aliceToken, bob.getId());

        mockMvc.perform(get("/api/buddies/sent").header("Authorization", bearer(aliceToken)))
                .andExpect(jsonPath("$[0].direction").value("OUTGOING"))
                .andExpect(jsonPath("$[0].user.username").value("bob"));

        mockMvc.perform(get("/api/buddies/pending").header("Authorization", bearer(bobToken)))
                .andExpect(jsonPath("$[0].direction").value("INCOMING"))
                .andExpect(jsonPath("$[0].user.username").value("alice"));
    }

    @Test
    @DisplayName("accepting a request makes both people buddies")
    void acceptingCreatesTheConnection() throws Exception {
        sendRequest(aliceToken, bob.getId());

        Long requestId = studyBuddyRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/buddies/accept/" + requestId)
                        .header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk());

        // The connection is symmetric: each sees the other.
        mockMvc.perform(get("/api/buddies").header("Authorization", bearer(aliceToken)))
                .andExpect(jsonPath("$[0].username").value("bob"));

        mockMvc.perform(get("/api/buddies").header("Authorization", bearer(bobToken)))
                .andExpect(jsonPath("$[0].username").value("alice"));
    }

    @Test
    @DisplayName("only the recipient can accept a request")
    void senderCannotAcceptTheirOwnRequest() throws Exception {
        sendRequest(aliceToken, bob.getId());

        Long requestId = studyBuddyRepository.findAll().get(0).getId();

        // Alice sent it, so Alice must not be able to accept it on Bob's behalf.
        mockMvc.perform(post("/api/buddies/accept/" + requestId)
                        .header("Authorization", bearer(aliceToken)))
                .andExpect(status().isBadRequest());

        assertThat(studyBuddyRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("you cannot add yourself")
    void cannotAddYourself() throws Exception {
        mockMvc.perform(post("/api/buddies/request")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("buddyId", alice.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("you cannot send the same request twice")
    void cannotDuplicateARequest() throws Exception {
        sendRequest(aliceToken, bob.getId());

        mockMvc.perform(post("/api/buddies/request")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("buddyId", bob.getId()))))
                .andExpect(status().isBadRequest());

        assertThat(studyBuddyRepository.findAll()).hasSize(1);
    }

    /* ================================================================
     * Finding people
     * ================================================================ */

    @Test
    @DisplayName("search finds people by username and by display name")
    void searchMatchesBothFields() throws Exception {
        mockMvc.perform(get("/api/buddies/search?q=bob").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user.username").value("bob"));

        // "Brown" is only in the display name, not the username.
        mockMvc.perform(get("/api/buddies/search?q=brown")
                        .header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user.username").value("bob"));
    }

    @Test
    @DisplayName("search never returns you to yourself")
    void searchExcludesTheSearcher() throws Exception {
        mockMvc.perform(get("/api/buddies/search?q=alice")
                        .header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("a one-character query returns nothing rather than everybody")
    void shortQueryReturnsNothing() throws Exception {
        mockMvc.perform(get("/api/buddies/search?q=a").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("search results report your relationship, and it updates as it changes")
    void searchReportsRelationship() throws Exception {
        // Before any request
        mockMvc.perform(get("/api/buddies/search?q=bob").header("Authorization", bearer(aliceToken)))
                .andExpect(jsonPath("$[0].relationship").value("NONE"));

        sendRequest(aliceToken, bob.getId());

        // Alice sees it as sent; Bob sees the same connection as received.
        mockMvc.perform(get("/api/buddies/search?q=bob").header("Authorization", bearer(aliceToken)))
                .andExpect(jsonPath("$[0].relationship").value("REQUEST_SENT"));

        mockMvc.perform(get("/api/buddies/search?q=alice").header("Authorization", bearer(bobToken)))
                .andExpect(jsonPath("$[0].relationship").value("REQUEST_RECEIVED"))
                // The request id comes along, so Bob can accept from search.
                .andExpect(jsonPath("$[0].requestId").isNumber());
    }

    @Test
    @DisplayName("shared interests are matched case-insensitively")
    void sharedInterestsIgnoreCase() throws Exception {
        /*
         * Alice has "Programming, Mathematics, Physics" and Bob has
         * "programming, mathematics, Cooking". Two overlap, in different
         * capitalisation - which must still count, since interests are free
         * text typed by people.
         */
        String body = mockMvc.perform(get("/api/buddies/search?q=bob")
                        .header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var shared = objectMapper.readTree(body).get(0).get("sharedInterests");

        assertThat(shared).hasSize(2);
        assertThat(shared.toString().toLowerCase())
                .contains("programming")
                .contains("mathematics")
                .doesNotContain("physics")
                .doesNotContain("cooking");
    }

    @Test
    @DisplayName("suggestions surface people who share interests")
    void suggestionsRankBySharedInterests() throws Exception {
        mockMvc.perform(get("/api/buddies/suggestions").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user.username").value("bob"))
                .andExpect(jsonPath("$[0].relationship").value("NONE"));
    }

    @Test
    @DisplayName("suggestions exclude people you already have a connection with")
    void suggestionsExcludeExistingConnections() throws Exception {
        sendRequest(aliceToken, bob.getId());

        // Suggesting someone you asked yesterday is noise.
        mockMvc.perform(get("/api/buddies/suggestions").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("buddy endpoints require a token")
    void buddyEndpointsAreProtected() throws Exception {
        mockMvc.perform(get("/api/buddies")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/buddies/pending")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/buddies/search?q=bob")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/buddies/suggestions")).andExpect(status().isUnauthorized());
    }

}
