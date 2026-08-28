package com.studygram.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/*
 * AiChatRequest - A conversation, not a single message
 *
 * WHY THIS REPLACED { "message": "..." }
 *
 * The assistant used to receive only the message you had just typed. Nothing
 * else. Which meant this happened, every time:
 *
 *     You: Explain recursion
 *     AI:  (a good explanation)
 *     You: Give me an example
 *     AI:  An example of what?
 *
 * It looked like a chat and behaved like a series of strangers. Language models
 * hold no state between calls - the ONLY thing they know is what you send, so
 * the history has to be sent with every request.
 *
 * That is also what makes "summarise this" and "test me on this" possible:
 * "this" has to refer to something, and until now there was nothing for it to
 * refer to.
 */
@Data
public class AiChatRequest {

    /*
     * The conversation so far, oldest first, with the new message last.
     *
     * The client keeps the history and sends it each time. That is normal for a
     * stateless API - it means the server stores no conversations, so there is
     * nothing to leak and nothing to clean up.
     *
     * The trade-off is that the client could send a history that never happened.
     * Here that is harmless: it only shapes the reply the sender gets back. If
     * these conversations were ever stored or shown to anyone else, they would
     * need to come from the server instead.
     */
    private List<AiMessage> messages = new ArrayList<>();

    /* Only used by the practice-questions endpoint. */
    private Integer count;

    /*
     * One turn. `role` is "user" or "ai", matching what the frontend already
     * uses; AIService translates "ai" into the "assistant" role the API expects.
     */
    @Data
    public static class AiMessage {
        private String role;
        private String content;
    }

}
