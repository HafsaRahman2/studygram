package com.studygram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studygram.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Shared setup for the integration tests.
 *
 * WHY A BASE CLASS
 *
 * Spring reuses one application context - and therefore one in-memory database
 * - across every test class in a run. That makes the suite fast, but it means
 * rows left behind by one class are still there when the next one starts.
 *
 * Both test classes were independently wiping the tables, and the second one
 * got the order wrong: it deleted users while posts created by the first class
 * still referenced them, and every one of its fifteen tests failed in setUp
 * with a foreign key violation.
 *
 * The fix is to write the wipe once, correctly, here.
 *
 * ORDER MATTERS, for the same reason PostService.deletePost has to be careful:
 * a database will not delete a row that other rows still point at. Children
 * first, parents last.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;

    @Autowired protected UserRepository userRepository;
    @Autowired protected PostRepository postRepository;
    @Autowired protected CommentRepository commentRepository;
    @Autowired protected HelpfulRepository helpfulRepository;
    @Autowired protected PasswordResetTokenRepository resetTokenRepository;
    @Autowired protected BreakSessionRepository breakSessionRepository;
    @Autowired protected StudyBuddyRepository studyBuddyRepository;
    @Autowired protected BCryptPasswordEncoder passwordEncoder;

    protected static final String PASSWORD = "password123";

    /*
     * Runs before every test in every subclass.
     *
     * Subclasses add their own @BeforeEach for fixtures; JUnit always runs the
     * superclass's first, so the tables are empty by the time they do.
     */
    @BeforeEach
    void wipeDatabase() {
        helpfulRepository.deleteAll();
        commentRepository.deleteAll();
        postRepository.deleteAll();
        breakSessionRepository.deleteAll();
        resetTokenRepository.deleteAll();
        studyBuddyRepository.deleteAll();
        userRepository.deleteAll();
    }

    /* Log in over the real endpoint and return the token. */
    protected String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("emailOrPhone", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("token").asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

}
