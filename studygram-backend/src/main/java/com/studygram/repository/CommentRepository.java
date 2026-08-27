package com.studygram.repository;

import com.studygram.entity.Comment;
import com.studygram.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/*
 * CommentRepository - Database operations for comments
 *
 * FREE methods from JpaRepository:
 *   - save(comment)
 *   - findById(id)
 *   - deleteById(id)
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /*
     * Get all comments on a specific post
     *
     * "findByPost" → SELECT * FROM comments WHERE post_id = ?
     * "OrderByCreatedAtAsc" → oldest first (natural conversation order)
     */
    List<Comment> findByPostOrderByCreatedAtAsc(Post post);

    /*
     * Count comments on a post
     * Useful for showing "5 comments" on a post
     */
    int countByPost(Post post);

    /*
     * Delete every comment on a post.
     *
     * Needed before deleting the post itself: comments hold a foreign key to
     * posts, and the database will not let you delete a row that other rows
     * still point at.
     */
    void deleteByPost(Post post);

    /*
     * Count the comments on MANY posts at once.
     *
     * THE N+1 QUERY PROBLEM
     *
     * The feed used to call countByPost() once per post. Showing 50 posts meant
     * 50 separate round trips to the database (plus 50 more to re-load each
     * post first) - the classic "N+1 queries" mistake, where one query to fetch
     * a list turns into N extra queries to decorate it.
     *
     * This does the whole job in one query: GROUP BY hands back one row per
     * post with its count, and the service turns those rows into a lookup map.
     *
     * Each row comes back as an Object[] of {postId, count}.
     */
    @Query("SELECT c.post.id, COUNT(c) FROM Comment c WHERE c.post IN :posts GROUP BY c.post.id")
    List<Object[]> countByPosts(@Param("posts") List<Post> posts);

    /* Has the AI already answered this post? Stops it being asked twice. */
    boolean existsByPostAndAiGeneratedTrue(Post post);

    /*
     * Which of these posts already have an AI answer.
     *
     * Batched for the same reason as countByPosts: asking per post would put
     * the N+1 problem straight back into the feed.
     */
    @Query("SELECT DISTINCT c.post.id FROM Comment c "
         + "WHERE c.post IN :posts AND c.aiGenerated = true")
    List<Long> findPostIdsWithAiAnswer(@Param("posts") List<Post> posts);

}
