package com.studygram.controller;

import com.studygram.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/*
 * AIController - API endpoints for AI Study Assistant
 *
 * Endpoints:
 *   POST /api/ai/chat      → General chat with AI
 *   POST /api/ai/explain   → Explain a topic
 *   POST /api/ai/practice  → Generate practice questions
 *   POST /api/ai/summarize → Summarize text
 */
@RestController
@RequestMapping("/api/ai")
public class AIController {

    @Autowired
    private AIService aiService;

    /*
     * CHAT - General conversation with AI
     *
     * URL: POST /api/ai/chat
     *
     * Request body:
     * {
     *   "message": "What is recursion?"
     * }
     *
     * Response:
     * {
     *   "response": "Recursion is when a function calls itself..."
     * }
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> request) {
        try {

            String message = request.get("message");

            if (message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Message cannot be empty");
            }

            String response = aiService.chat(message);

            return ResponseEntity.ok(Map.of("response", response));

        } catch (Exception e) {
            return ResponseEntity.status(500).body("AI service error: " + e.getMessage());
        }
    }

    /*
     * EXPLAIN - Get explanation of a topic
     *
     * URL: POST /api/ai/explain
     *
     * Request body:
     * {
     *   "topic": "binary search trees"
     * }
     */
    @PostMapping("/explain")
    public ResponseEntity<?> explain(@RequestBody Map<String, String> request) {
        try {

            String topic = request.get("topic");

            if (topic == null || topic.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Topic cannot be empty");
            }

            String response = aiService.explain(topic);

            return ResponseEntity.ok(Map.of(
                    "topic", topic,
                    "explanation", response
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body("AI service error: " + e.getMessage());
        }
    }

    /*
     * PRACTICE - Generate practice questions
     *
     * URL: POST /api/ai/practice
     *
     * Request body:
     * {
     *   "topic": "sorting algorithms",
     *   "count": 5
     * }
     */
    @PostMapping("/practice")
    public ResponseEntity<?> practice(@RequestBody Map<String, Object> request) {
        try {

            String topic = (String) request.get("topic");
            Integer count = (Integer) request.getOrDefault("count", 5);

            if (topic == null || topic.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Topic cannot be empty");
            }

            String response = aiService.generatePracticeQuestions(topic, count);

            return ResponseEntity.ok(Map.of(
                    "topic", topic,
                    "questionCount", count,
                    "questions", response
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body("AI service error: " + e.getMessage());
        }
    }

    /*
     * SUMMARIZE - Summarize notes or text
     *
     * URL: POST /api/ai/summarize
     *
     * Request body:
     * {
     *   "text": "Long notes about data structures..."
     * }
     */
    @PostMapping("/summarize")
    public ResponseEntity<?> summarize(@RequestBody Map<String, String> request) {
        try {

            String text = request.get("text");

            if (text == null || text.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Text cannot be empty");
            }

            String response = aiService.summarize(text);

            return ResponseEntity.ok(Map.of("summary", response));

        } catch (Exception e) {
            return ResponseEntity.status(500).body("AI service error: " + e.getMessage());
        }
    }

}
