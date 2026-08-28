package com.studygram;

import com.studygram.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/*
 * Editing your profile obeys the same rules as creating your account.
 *
 * THE BUG THESE TESTS EXIST FOR
 *
 * Signup makes you choose between two and five interests, because the
 * personalized feed has nothing to match on otherwise. That check lived only in
 * the signup path. Once you had an account, the profile would save an empty
 * list quite happily - and For You then stayed empty forever, with nothing on
 * screen connecting the dead feed to the edit you had made a week earlier.
 *
 * The same gap ran the other way: the profile accepted ten interests that
 * signup would have refused.
 *
 * A rule enforced at only one of the two doors is not a rule.
 */
class ProfileIntegrationTest extends IntegrationTestBase {

    private User alice;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        alice = new User();
        alice.setName("Alice");
        alice.setUsername("alice");
        alice.setEmail("alice@test.com");
        alice.setPassword(passwordEncoder.encode(PASSWORD));
        alice.setInterests("Programming, Mathematics");
        alice = userRepository.save(alice);

        token = login("alice@test.com");
    }

    /* PUT /api/profile with the given body, returning the HTTP status. */
    private int update(Map<String, Object> body) throws Exception {
        return mockMvc.perform(put("/api/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getStatus();
    }

    private String storedInterests() {
        return userRepository.findById(alice.getId()).orElseThrow().getInterests();
    }

    @Test
    @DisplayName("cannot empty your interests and silently break For You")
    void rejectsEmptyInterests() throws Exception {
        assertThat(update(Map.of("interests", ""))).isEqualTo(400);

        assertThat(storedInterests())
                .as("a rejected update must not have been written")
                .isEqualTo("Programming, Mathematics");
    }

    @Test
    @DisplayName("cannot drop below the two interests signup required")
    void rejectsTooFewInterests() throws Exception {
        assertThat(update(Map.of("interests", "Programming"))).isEqualTo(400);
        assertThat(storedInterests()).isEqualTo("Programming, Mathematics");
    }

    @Test
    @DisplayName("cannot exceed the five interests signup allowed")
    void rejectsTooManyInterests() throws Exception {
        assertThat(update(Map.of(
                "interests", "Programming, Mathematics, Physics, Chemistry, Biology, Statistics")))
                .isEqualTo(400);

        assertThat(storedInterests()).isEqualTo("Programming, Mathematics");
    }

    @Test
    @DisplayName("a valid change to interests is saved")
    void acceptsValidInterests() throws Exception {
        assertThat(update(Map.of("interests", "Physics, Chemistry, Biology"))).isEqualTo(200);
        assertThat(storedInterests()).isEqualTo("Physics, Chemistry, Biology");
    }

    @Test
    @DisplayName("editing only your name does not require sending interests")
    void otherFieldsAreUnaffected() throws Exception {
        /*
         * Every field on this endpoint is optional - a missing one means "leave
         * this alone". If the interests check ran on absent values too, then
         * changing your name would demand you re-send interests you had not
         * touched, and legacy accounts with none could never edit anything.
         */
        assertThat(update(Map.of("name", "Alice Cooper"))).isEqualTo(200);

        User updated = userRepository.findById(alice.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Alice Cooper");
        assertThat(updated.getInterests()).isEqualTo("Programming, Mathematics");
    }

    @Test
    @DisplayName("a rejected profile edit answers 400, not 404")
    void validationFailureIsNotAFourOhFour() throws Exception {
        /*
         * This endpoint used to answer every failure with 404. A 404 tells the
         * client the thing does not exist and there is no point retrying, which
         * is the opposite of what "pick two interests" means - and the frontend
         * shows the body text either way, so nothing looked wrong on screen
         * while the status code said something quite different.
         */
        mockMvc.perform(put("/api/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("interests", "Physics"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("at least 2")));
    }

}
