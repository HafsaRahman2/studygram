package com.studygram.service;

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

        // If no key is configured, say so clearly instead of sending a doomed
        // request and surfacing a confusing 401 to the user.
        if (apiKey == null || apiKey.isBlank()) {
            return "The AI assistant is not configured on this server. "
                    + "Set the GROQ_API_KEY environment variable and restart the backend. "
                    + "You can get a free key at https://console.groq.com/keys";
        }

        // Set up HTTP headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);  // Authorization: Bearer gsk_...

        // Build request body (Groq uses OpenAI-compatible format)
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.7);  // Creativity level (0-1)
        requestBody.put("max_tokens", 1024);  // Max response length

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

        } catch (Exception e) {
            return "Sorry, I couldn't process your request. Error: " + e.getMessage();
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
