package com.studygram.controller;

import com.studygram.dto.AiChatRequest;
import com.studygram.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/*
 * AIController - The study assistant
 *
 * Endpoints:
 *   POST /api/ai/chat      → continue the conversation
 *   POST /api/ai/summarize → revision notes on what has been discussed
 *   POST /api/ai/practice  → practice questions on what has been discussed
 *
 * ALL THREE TAKE THE CONVERSATION, NOT A TOPIC.
 *
 * That is the difference between this and what was here before. The old
 * endpoints each took one string - a topic to explain, text to summarise - so
 * every request stood alone and the assistant could not answer "give me an
 * example" because it had no idea what of.
 *
 * Now the client sends the history, and "summarise this" and "test me on this"
 * finally have a "this" to refer to.
 *
 * WHAT WAS DELETED
 *
 * /api/ai/explain took a topic and explained it - which is just chatting, so it
 * earned nothing. The old /practice and /summarize took a topic and a blob of
 * text respectively; both are replaced by the conversation-aware versions here.
 *
 * They were removed rather than left in place. An endpoint nothing calls is an
 * endpoint nobody reviews, which is exactly how the password hashes ended up
 * being served from /api/buddies/pending for months.
 */
@RestController
@RequestMapping("/api/ai")
public class AIController {

    @Autowired
    private AIService aiService;

    /* Practice questions, when the client does not say how many. */
    private static final int DEFAULT_PRACTICE_QUESTIONS = 5;

    /*
     * CONTINUE THE CONVERSATION
     *
     * Body: { "messages": [ {"role":"user","content":"..."},
     *                       {"role":"ai","content":"..."} ] }
     *
     * Oldest first, with the new message last.
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody AiChatRequest request) {
        try {

            if (request.getMessages() == null || request.getMessages().isEmpty()) {
                return ResponseEntity.badRequest().body("Say something first");
            }

            return ResponseEntity.ok(Map.of("response", aiService.chat(request.getMessages())));

        } catch (AIService.AiUnavailableException e) {
            /*
             * 503, not 500. The assistant being unreachable is a temporary
             * condition of a dependency, not a bug in this request - and the
             * status code should say which, because a client can sensibly retry
             * one and not the other.
             */
            return ResponseEntity.status(503).body(e.getMessage());

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /*
     * REVISION NOTES ON THIS CONVERSATION
     *
     * "We have been going back and forth about recursion for ten minutes -
     * write that down so I can revise from it."
     */
    @PostMapping("/summarize")
    public ResponseEntity<?> summarize(@RequestBody AiChatRequest request) {
        try {

            if (request.getMessages() == null || request.getMessages().isEmpty()) {
                return ResponseEntity.badRequest().body("There is nothing to summarise yet");
            }

            return ResponseEntity.ok(Map.of(
                    "response", aiService.summarizeConversation(request.getMessages())));

        } catch (AIService.AiUnavailableException e) {
            return ResponseEntity.status(503).body(e.getMessage());

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /*
     * PRACTICE QUESTIONS ON THIS CONVERSATION
     *
     * "Now test me on what we just went through."
     */
    @PostMapping("/practice")
    public ResponseEntity<?> practice(@RequestBody AiChatRequest request) {
        try {

            if (request.getMessages() == null || request.getMessages().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("Chat about something first, then I can test you on it");
            }

            int count = request.getCount() == null
                    ? DEFAULT_PRACTICE_QUESTIONS
                    // Math.clamp is Java 21; this project targets 17.
                    : Math.max(1, Math.min(request.getCount(), 10));

            return ResponseEntity.ok(Map.of(
                    "response", aiService.practiceFromConversation(request.getMessages(), count)));

        } catch (AIService.AiUnavailableException e) {
            return ResponseEntity.status(503).body(e.getMessage());

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
