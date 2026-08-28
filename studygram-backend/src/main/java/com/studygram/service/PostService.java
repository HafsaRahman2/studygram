package com.studygram.service;

import com.studygram.dto.PostResponse;
import com.studygram.entity.Helpful;
import com.studygram.entity.Post;
import com.studygram.entity.User;
import com.studygram.repository.CommentRepository;
import com.studygram.repository.HelpfulRepository;
import com.studygram.repository.PostRepository;
import com.studygram.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/*
 * PostService - Business logic for Post operations
 *
 * Handles:
 *   - Creating new posts (regular or anonymous)
 *   - Getting feed (all posts)
 *   - Getting user's posts
 *   - Marking posts as helpful
 */
@Service
public class PostService {

    /*
     * We need both repositories:
     *   - PostRepository: to save/read posts
     *   - UserRepository: to find the user creating the post
     */
    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HelpfulRepository helpfulRepository;

    @Autowired
    private CommentRepository commentRepository;

    /* Limits enforced on the server, so they hold no matter who calls the API. */
    private static final int MAX_POST_LENGTH = 2000;
    private static final int MAX_TOPICS_PER_POST = 5;

    /*
     * CREATE POST
     *
     * Takes: userId (who's posting), post content, and anonymous flag
     * Returns: the saved post
     *
     * Even anonymous posts are linked to a user in database
     * (so we know who posted it), but we hide the user info
     * when displaying anonymous posts
     */
    public Post createPost(Long userId, Post post) {

        // Find the user who is creating this post
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        /*
         * Validate on the SERVER, not just in the browser.
         *
         * The React form already checks these, but anyone can call this API
         * directly with curl and skip the form entirely. Client-side validation
         * is a convenience for honest users; server-side validation is the rule.
         */
        if (post.getContent() == null || post.getContent().isBlank()) {
            throw new RuntimeException("Post content cannot be empty");
        }

        if (post.getContent().length() > MAX_POST_LENGTH) {
            throw new RuntimeException("Post is too long (max " + MAX_POST_LENGTH + " characters)");
        }

        if (post.getTopics() == null || post.getTopics().isEmpty()) {
            throw new RuntimeException("Please choose at least one topic for your post");
        }

        if (post.getTopics().size() > MAX_TOPICS_PER_POST) {
            throw new RuntimeException("A post can have at most " + MAX_TOPICS_PER_POST + " topics");
        }

        /*
         * Only the two known kinds are accepted. Anything else - a typo, or a
         * client sending something inventive - becomes a plain SHARE rather
         * than a row nothing knows how to render.
         */
        if (!Post.TYPE_QUESTION.equals(post.getPostType())) {
            post.setPostType(Post.TYPE_SHARE);
        }

        // Attach the user to the post
        // (We always store who posted, but hide it if anonymous)
        post.setUser(user);
        post.setContent(post.getContent().trim());

        // Save and return
        return postRepository.save(post);
    }

    /*
     * GET FEED - All posts, newest first
     *
     * This is what shows on the main feed/timeline
     * For anonymous posts, we'll hide user info in controller
     */
    public List<Post> getFeed() {
        return postRepository.findAllWithTopics();
    }

    /*
     * Turn a user's stored interests string into a clean, lowercase list.
     *
     * Interests are saved as one comma-separated string: "Programming, Mathematics".
     * Splitting naively on "," gives ["Programming", " Mathematics"] - note the
     * leading space on the second item, which silently broke every comparison
     * that used it.
     *
     * So: split, trim the whitespace, lowercase for case-insensitive matching,
     * and drop anything empty (which is what a trailing comma leaves behind).
     */
    public static List<String> parseTopics(String commaSeparated) {

        if (commaSeparated == null || commaSeparated.isBlank()) {
            return List.of();
        }

        return Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());
    }

    /*
     * GET PERSONALIZED FEED - Only posts matching user's interests
     *
     * How it works:
     * 1. Get user's interests: "programming,math,business"
     * 2. Split into list: ["programming", "math", "business"]
     * 3. Find posts where topic is in that list
     *
     * Result: User only sees posts about their interests
     *         No distractions from other topics!
     */
    public List<Post> getPersonalizedFeed(Long userId) {

        // Find the user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Turn "Programming, Mathematics" into ["programming", "mathematics"]
        List<String> topicsList = parseTopics(user.getInterests());

        // No interests set yet, so there is nothing to personalize on.
        // Fall back to the main feed rather than showing the user a blank page.
        if (topicsList.isEmpty()) {
            return getFeed();
        }

        // Find posts tagged with any of these topics
        return postRepository.findByTopicsIn(topicsList);
    }

    /*
     * BUILD THE RESPONSE FOR A LIST OF POSTS
     *
     * Every feed endpoint needs the same three things per post: its comment
     * count, who marked it helpful, and whether the viewer owns it. Doing that
     * per post meant hundreds of queries for a single feed (see the note in
     * CommentRepository.countByPosts).
     *
     * This does it in a fixed number of queries no matter how many posts there
     * are: one for the posts themselves, one for all the comment counts, one
     * for all the helpful marks. Then it stitches them together in memory,
     * which is essentially free compared to a database round trip.
     *
     * @param posts    the posts to convert
     * @param viewerId who is looking (may be null) - decides `ownPost`
     */
    public List<PostResponse> toResponses(List<Post> posts, Long viewerId) {

        if (posts.isEmpty()) {
            return List.of();
        }

        // ONE query: postId -> number of comments
        Map<Long, Integer> commentCounts = new HashMap<>();
        for (Object[] row : commentRepository.countByPosts(posts)) {
            commentCounts.put((Long) row[0], ((Number) row[1]).intValue());
        }

        // ONE query: postId -> list of usernames who found it helpful
        Map<Long, List<String>> helpfulUsers = new HashMap<>();
        for (Object[] row : helpfulRepository.findUsernamesByPosts(posts)) {
            helpfulUsers
                    .computeIfAbsent((Long) row[0], k -> new ArrayList<>())
                    .add((String) row[1]);
        }

        // Stitch it together
        return posts.stream()
                .map(post -> {
                    PostResponse response = PostResponse.fromPost(
                            post,
                            commentCounts.getOrDefault(post.getId(), 0),
                            viewerId
                    );
                    response.setHelpfulUsers(
                            helpfulUsers.getOrDefault(post.getId(), List.of())
                    );
                    return response;
                })
                .collect(Collectors.toList());
    }

    /*
     * GET USER'S POSTS - Posts by a specific user
     *
     * Used for profile pages: "Show Hafsa's posts"
     * Note: This only shows their NON-anonymous posts to others
     */
    public List<Post> getPostsByUser(Long userId) {

        // Find the user first
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Get their posts, newest first
        return postRepository.findByUserWithTopics(user);
    }

    /*
     * GET SINGLE POST by ID
     */
    public Post getPostById(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
    }

    /*
     * TOGGLE HELPFUL - Add or remove helpful mark
     *
     * If user hasn't marked → add mark
     * If user already marked → remove mark (toggle off)
     *
     * Returns: true if now marked, false if unmarked
     */
    public boolean toggleHelpful(Long postId, Long userId) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if already marked
        Optional<Helpful> existing = helpfulRepository.findByUserAndPost(user, post);

        if (existing.isPresent()) {
            // Already marked → remove it (toggle off)
            helpfulRepository.delete(existing.get());
            // Update count
            post.setHelpfulCount(Math.max(0, post.getHelpfulCount() - 1));
            postRepository.save(post);
            return false;  // Now unmarked
        } else {
            // Not marked → add it
            Helpful helpful = new Helpful();
            helpful.setUser(user);
            helpful.setPost(post);
            helpfulRepository.save(helpful);
            // Update count
            post.setHelpfulCount(post.getHelpfulCount() + 1);
            postRepository.save(post);
            return true;  // Now marked
        }
    }

    /*
     * CHECK IF USER MARKED POST AS HELPFUL
     */
    public boolean hasUserMarkedHelpful(Long postId, Long userId) {
        Post post = postRepository.findById(postId).orElse(null);
        User user = userRepository.findById(userId).orElse(null);

        if (post == null || user == null) return false;

        return helpfulRepository.existsByUserAndPost(user, post);
    }

    /*
     * GET USERS WHO MARKED POST AS HELPFUL
     *
     * Returns list of usernames who found this post helpful
     */
    public List<String> getHelpfulUsers(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        List<Helpful> helpfuls = helpfulRepository.findByPost(post);

        return helpfuls.stream()
                .map(h -> h.getUser().getUsername())
                .collect(Collectors.toList());
    }

    /*
     * MARK A QUESTION ANSWERED (or un-mark it)
     *
     * Only the person who asked can decide their question is answered - they
     * are the one who knows whether it actually helped. Letting anyone mark it
     * would make the signal worthless.
     */
    @Transactional
    public Post toggleResolved(Long postId, Long userId) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (!post.getUser().getId().equals(userId)) {
            throw new RuntimeException("Only the person who asked can mark this answered");
        }

        if (!post.isQuestion()) {
            throw new RuntimeException("Only questions can be marked answered");
        }

        post.setResolved(!post.isResolved());
        return postRepository.save(post);
    }

    /*
     * DELETE POST
     *
     * Only the owner should be able to delete.
     *
     * ORDER MATTERS HERE.
     *
     * Comments and helpful marks both store a post_id foreign key. A database
     * will refuse to delete a row that other rows still reference - otherwise
     * those rows would point at nothing. So the children have to go first:
     *
     *     helpfuls  ->  comments  ->  post
     *
     * @Transactional wraps all three deletes in a single database transaction.
     * If any one of them fails, every one of them is rolled back, so we can
     * never end up with a post whose comments were deleted but which still
     * exists itself.
     */
    @Transactional
    public void deletePost(Long postId, Long userId) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        // Check if this user owns the post
        if (!post.getUser().getId().equals(userId)) {
            throw new RuntimeException("You can only delete your own posts");
        }

        // Remove everything that points at this post, then the post itself
        helpfulRepository.deleteByPost(post);
        commentRepository.deleteByPost(post);
        postRepository.delete(post);
    }

}
