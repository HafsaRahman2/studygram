package com.studygram.repository;

import com.studygram.entity.Post;
import com.studygram.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/*
 * PostRepository - Database operations for Post entity
 *
 * Extends JpaRepository<Post, Long>:
 *   - Post: the entity we're managing
 *   - Long: the type of the ID field
 *
 * FREE methods from JpaRepository:
 *   - save(post)        → saves a post
 *   - findById(id)      → finds post by ID
 *   - findAll()         → gets all posts
 *   - deleteById(id)    → deletes a post
 */
public interface PostRepository extends JpaRepository<Post, Long> {

    /*
     * Find all posts by a specific user
     *
     * Spring reads method name → creates query:
     * SELECT * FROM posts WHERE user_id = ?
     *
     * Used for: "Show me all of Hafsa's posts"
     */
    List<Post> findByUser(User user);

    /*
     * Find all posts, newest first
     *
     * OrderByCreatedAtDesc means:
     *   - Order by createdAt column
     *   - Desc = descending (newest first)
     *
     * Used for: Feed/timeline showing latest posts
     */
    List<Post> findAllByOrderByCreatedAtDesc();

    /*
     * Find posts by a user, newest first
     *
     * Used for: User's profile page showing their posts
     */
    List<Post> findByUserOrderByCreatedAtDesc(User user);

    /*
     * Find posts matching any of the given topics, newest first.
     *
     * Used for the personalized "For You" feed.
     *
     * TWO THINGS TO NOTICE IN THIS QUERY
     *
     * 1. LOWER(t) IN :topics
     *    Topics are stored in display form ("Web Development") but compared in
     *    lowercase, so a user who typed "web development" still matches. Pass
     *    this method a list that is already lowercased.
     *
     * 2. The subquery.
     *    The obvious version - JOIN p.topics t WHERE LOWER(t) IN :topics - would
     *    work for filtering, but because we also LEFT JOIN FETCH the topics to
     *    load them, the join would filter the fetched collection too: a post
     *    about both Programming and Cooking would come back looking as though
     *    it were only about Programming.
     *
     *    So we do it in two parts: the subquery decides WHICH posts qualify,
     *    and the outer LEFT JOIN FETCH loads ALL of each winning post's topics.
     */
    @Query("SELECT DISTINCT p FROM Post p LEFT JOIN FETCH p.topics "
         + "WHERE p.id IN ("
         + "  SELECT p2.id FROM Post p2 JOIN p2.topics t WHERE LOWER(t) IN :topics"
         + ") "
         + "ORDER BY p.createdAt DESC")
    List<Post> findByTopicsIn(@Param("topics") List<String> topics);

    /*
     * The main feed - every post, newest first.
     *
     * LEFT JOIN FETCH pulls each post AND its topics in a single query. Without
     * it, Hibernate would run one extra query per post to load its topics -
     * the classic "N+1 query" problem, where showing 50 posts costs 51 trips to
     * the database instead of 1.
     */
    @Query("SELECT DISTINCT p FROM Post p LEFT JOIN FETCH p.topics "
         + "ORDER BY p.createdAt DESC")
    List<Post> findAllWithTopics();

    /*
     * One user's posts, newest first, topics included. Used on profile pages.
     */
    @Query("SELECT DISTINCT p FROM Post p LEFT JOIN FETCH p.topics "
         + "WHERE p.user = :user ORDER BY p.createdAt DESC")
    List<Post> findByUserWithTopics(@Param("user") User user);

}
