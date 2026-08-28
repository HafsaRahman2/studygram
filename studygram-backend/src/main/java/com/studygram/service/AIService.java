package com.studygram.service;

import com.studygram.dto.AiChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.*;

/*
 * AIService - Connects to Groq AI API for study assistance
 *
 * Groq provides free AI API access with fast responses.
 * We use their llama model for answering study questions.
 *
 * How it works:
 * 1. User sends a question: "Explain recursion"
 * 2. We send it to Groq API
 * 3. Groq returns AI-generated answer
 * 4. We return answer to user
 */
@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    /*
     * @Value reads from application.properties, which in turn reads from the
     * GROQ_API_KEY environment variable. The key is never written in source code.
     */
    @Value("${groq.api.key}")
    private String apiKey;

    /*
     * Which Groq model to use. Configurable so swapping models is a config
     * change, not a code change.
     */
    @Value("${groq.api.model}")
    private String model;

    /*
     * Groq API endpoint
     */
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    /*
     * RestTemplate - Spring's HTTP client for making API calls
     */
    private final RestTemplate restTemplate = new RestTemplate();

    /*
     * CHAT - Send a message to AI and get response
     *
     * This is the main method for the AI Study Assistant
     */
    /*
     * How many turns of history to send.
     *
     * Every message costs tokens, and the cost is paid on EVERY request - a
     * fifty-message conversation would send all fifty each time. Ten turns is
     * enough to keep a topic in view while keeping requests small and fast.
     *
     * Enforced here rather than in the browser, because the browser could send
     * a thousand and run up the bill.
     */
    private static final int MAX_HISTORY = 8;

    /*
     * TOKEN BUDGETING, and why these numbers are what they are.
     *
     * The free Groq tier allows 8,000 tokens PER MINUTE, and that budget counts
     * everything: the history sent up, plus the space reserved for the reply.
     *
     * Sending the conversation with every message - which is the only way the
     * assistant can remember anything - makes each request bigger than the last.
     * A long summary comes back, gets sent up again as history, and two or three
     * messages later the minute's budget is gone.
     *
     * So older turns are trimmed harder than recent ones. What was said five
     * messages ago usually needs to be present in outline; what was said last
     * needs to be present in full.
     */
    private static final int MAX_MESSAGE_CHARS = 2500;

    /* The newest turn is what the reply must actually respond to - keep it whole. */
    private static final int MAX_LATEST_MESSAGE_CHARS = 6000;

    /*
     * Turn our conversation into the shape the API expects.
     *
     * Two translations happen here:
     *   - our "ai" role becomes the API's "assistant"
     *   - the system prompt is prepended, always, and is never something the
     *     client can set. If a caller could supply the system message they
     *     could redefine what the assistant is, which is not theirs to decide.
     */
    private List<Map<String, String>> buildMessages(List<AiChatRequest.AiMessage> history) {

        List<Map<String, String>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content", "You are a helpful study assistant for students. "
                        + "Explain concepts clearly and simply. "
                        + "When asked for practice questions, provide good questions with answers. "
                        + "Be encouraging and supportive."
        ));

        if (history == null || history.isEmpty()) {
            return messages;
        }

        // Keep the most RECENT turns - the end of a conversation is what the
        // next reply has to follow on from.
        List<AiChatRequest.AiMessage> recent = history.size() > MAX_HISTORY
                ? history.subList(history.size() - MAX_HISTORY, history.size())
                : history;

        for (AiChatRequest.AiMessage turn : recent) {
            if (turn.getContent() == null || turn.getContent().isBlank()) {
                continue;
            }

            /*
             * The last turn keeps its full length; earlier ones are trimmed.
             * Trimming the message being replied to would mean answering half
             * a question.
             */
            boolean isLatest = turn == recent.get(recent.size() - 1);
            int limit = isLatest ? MAX_LATEST_MESSAGE_CHARS : MAX_MESSAGE_CHARS;

            String content = turn.getContent().length() > limit
                    ? turn.getContent().substring(0, limit) + "\n[...trimmed]"
                    : turn.getContent();

            // Anything that is not explicitly ours is treated as the user's.
            String role = "ai".equalsIgnoreCase(turn.getRole()) ? "assistant" : "user";

            messages.add(Map.of("role", role, "content", content));
        }

        return messages;
    }

    /*
     * CHAT - continue a conversation
     *
     * Takes the whole history rather than one message, because the model
     * remembers nothing between calls. See AiChatRequest for why.
     */
    public String chat(List<AiChatRequest.AiMessage> history) {

        /*
         * No key configured: fail loudly rather than returning prose.
         *
         * This used to RETURN an explanatory sentence, which read fine in the
         * chat window but was quietly disastrous once questions could have AI
         * answers - the explanation got saved to the database as though the AI
         * had answered the question. Throwing means callers cannot mistake a
         * failure for a reply.
         */
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiUnavailableException(
                    "The AI assistant is not configured on this server. "
                    + "Set GROQ_API_KEY and restart the backend. "
                    + "Free keys: https://console.groq.com/keys");
        }

        // Set up HTTP headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);  // Authorization: Bearer gsk_...

        // Build request body (Groq uses OpenAI-compatible format)
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.7);  // Creativity level (0-1)
        /*
         * Generous, because current models spend part of their budget on
         * internal reasoning before writing anything. With a small limit they
         * can burn the whole allowance thinking and return a single word - which
         * is exactly what happened while picking a replacement model.
         */
        requestBody.put("max_tokens", 1200);

        requestBody.put("messages", buildMessages(history));

        // Make the API call
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = postWithRetry(request);

            // Extract the AI's response from the API response
            Map body = response.getBody();
            List<Map> choices = (List<Map>) body.get("choices");
            Map firstChoice = choices.get(0);
            Map message = (Map) firstChoice.get("message");
            String aiResponse = (String) message.get("content");

            return aiResponse;

        } catch (AiUnavailableException e) {
            throw e;

        } catch (Exception e) {
            /*
             * Any failure - the model was decommissioned, the key was revoked,
             * Groq was down - must NOT come back as a string. A caller saving
             * the result would persist "Sorry, I couldn't process your request"
             * as the answer to somebody's question.
             *
             * That is not hypothetical: it is what this code did, and the
             * evidence was sitting in the database as an AI answer that was
             * really a 404.
             */
            log.warn("Groq request failed: {}", e.getMessage());
            throw new AiUnavailableException(
                    "The AI assistant is temporarily unavailable. Please try again shortly.");
        }
    }

    /*
     * Send the request, and retry ONCE if we are rate limited.
     *
     * Groq's 429 response says exactly how long to wait ("try again in 2.16s"),
     * so the polite thing is to wait that long and try again rather than handing
     * the user an error they can do nothing about except press the button again
     * themselves.
     *
     * Only one retry, and only for short waits. If the service says come back in
     * thirty seconds, blocking the request thread for thirty seconds is worse
     * than saying so - the user should be told, not left watching a spinner.
     */
    private ResponseEntity<Map> postWithRetry(HttpEntity<Map<String, Object>> request) {

        try {
            return restTemplate.postForEntity(GROQ_API_URL, request, Map.class);

        } catch (HttpClientErrorException.TooManyRequests e) {

            double waitSeconds = parseRetryAfter(e.getResponseBodyAsString());

            if (waitSeconds <= 0 || waitSeconds > MAX_RETRY_WAIT_SECONDS) {
                log.warn("Rate limited, wait of {}s is too long to hold the request", waitSeconds);
                throw new AiUnavailableException(
                        "The assistant is busy right now. Give it a few seconds and try again.");
            }

            log.info("Rate limited by Groq; retrying in {}s", waitSeconds);

            try {
                // +200ms of headroom, because the limit is measured on their clock.
                Thread.sleep((long) (waitSeconds * 1000) + 200);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AiUnavailableException("The assistant is busy. Please try again.");
            }

            try {
                return restTemplate.postForEntity(GROQ_API_URL, request, Map.class);
            } catch (HttpClientErrorException.TooManyRequests stillLimited) {
                throw new AiUnavailableException(
                        "You have hit the AI usage limit for this minute. "
                        + "Wait a moment and try again.");
            }
        }
    }

    /*
     * Pull the wait out of "Please try again in 2.159999999s".
     *
     * Groq tells us precisely how long to wait, which is far better than
     * guessing. Returns -1 if the message does not contain a wait, in which case
     * the caller gives up rather than inventing a delay.
     */
    private double parseRetryAfter(String errorBody) {
        if (errorBody == null) return -1;

        Matcher matcher = RETRY_AFTER.matcher(errorBody);
        if (!matcher.find()) return -1;

        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static final Pattern RETRY_AFTER =
            Pattern.compile("try again in ([0-9.]+)s");

    /* Longer than this and we tell the user instead of holding the request. */
    private static final double MAX_RETRY_WAIT_SECONDS = 8.0;

    /*
     * Thrown when the assistant cannot answer. Distinct from an ordinary
     * RuntimeException so callers can tell "the AI is down" apart from "you
     * asked for something invalid".
     */
    public static class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(String message) {
            super(message);
        }
    }

    /*
     * SUMMARISE THE CONVERSATION SO FAR
     *
     * Note what is being summarised: the DISCUSSION, not a block of text the
     * user pasted. That is the whole point - you have spent ten minutes working
     * something out with the assistant, and now you want it written down so you
     * can revise from it later.
     *
     * The instruction is appended as a final user turn, so the model sees the
     * conversation and then the request about it.
     */
    public String summarizeConversation(List<AiChatRequest.AiMessage> history) {
        return chat(withInstruction(history,
                "Summarise what we have covered in this conversation as concise revision notes. "
                + "Use short bullet points and keep the key definitions and examples."));
    }

    /*
     * PRACTICE QUESTIONS ON THE CONVERSATION
     *
     * Same idea: test me on what we just discussed, not on a topic I retype.
     */
    public String practiceFromConversation(List<AiChatRequest.AiMessage> history, int count) {
        return chat(withInstruction(history,
                "Based on what we have discussed, write " + count + " practice questions. "
                + "Put all the answers together at the end, after the questions, so I can "
                + "attempt them first. Make them progressively harder."));
    }

    /* Append an instruction to the conversation without mutating the caller's list. */
    private List<AiChatRequest.AiMessage> withInstruction(
            List<AiChatRequest.AiMessage> history, String instruction) {

        List<AiChatRequest.AiMessage> combined =
                new ArrayList<>(history == null ? List.of() : history);

        AiChatRequest.AiMessage ask = new AiChatRequest.AiMessage();
        ask.setRole("user");
        ask.setContent(instruction);
        combined.add(ask);

        return combined;
    }

}
