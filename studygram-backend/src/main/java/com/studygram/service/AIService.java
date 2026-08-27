package com.studygram.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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
    public String chat(String userMessage) {

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
        requestBody.put("max_tokens", 2048);

        // Messages array (conversation format)
        List<Map<String, String>> messages = new ArrayList<>();

        // System message - tells AI how to behave
        messages.add(Map.of(
                "role", "system",
                "content", "You are a helpful study assistant for students. " +
                        "Explain concepts clearly and simply. " +
                        "When asked for practice questions, provide good questions with answers. " +
                        "Be encouraging and supportive."
        ));

        // User's message
        messages.add(Map.of(
                "role", "user",
                "content", userMessage
        ));

        requestBody.put("messages", messages);

        // Make the API call
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    GROQ_API_URL,
                    request,
                    Map.class
            );

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
     * EXPLAIN - Ask AI to explain a topic
     * Adds context to get a better explanation
     */
    public String explain(String topic) {
        String prompt = "Please explain " + topic + " in simple terms. " +
                "Use examples if helpful. Make it easy for a student to understand.";
        return chat(prompt);
    }

    /*
     * PRACTICE QUESTIONS - Generate practice questions on a topic
     */
    public String generatePracticeQuestions(String topic, int count) {
        String prompt = "Generate " + count + " practice questions about " + topic + ". " +
                "Include the answers after each question. " +
                "Make them progressively harder.";
        return chat(prompt);
    }

    /*
     * SUMMARIZE - Summarize notes or text
     */
    public String summarize(String text) {
        String prompt = "Please summarize the following text concisely:\n\n" + text;
        return chat(prompt);
    }

}
