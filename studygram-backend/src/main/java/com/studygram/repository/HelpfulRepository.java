package com.studygram.repository;

import com.studygram.entity.Helpful;
import com.studygram.entity.Post;
import com.studygram.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/*
 * HelpfulRepository - Database operations for helpful marks
 */
public interface HelpfulRepository extends JpaRepository<Helpful, Long> {

    // Find if a user already marked a post as helpful
    Optional<Helpful> findByUserAndPost(User user, Post post);

    // Check if user marked post as helpful
    boolean existsByUserAndPost(User user, Post post);

    // Get all helpfuls for a post (to show who marked it)
    List<Helpful> findByPost(Post post);

    // Count helpfuls for a post
    int countByPost(Post post);

    /*
     * Delete every helpful mark on a post.
     * Same reason as CommentRepository.deleteByPost - clear the children
     * before deleting the parent row.
     */
    void deleteByPost(Post post);

    /*
     * Get every (postId, username) pair for a batch of posts in one query.
     *
     * Same reasoning as CommentRepository.countByPosts - one query for the
     * whole feed instead of one query per post.
     *
     * Each row comes back as an Object[] of {postId, username}.
     */
    @Query("SELECT h.post.id, h.user.username FROM Helpful h WHERE h.post IN :posts")
    List<Object[]> findUsernamesByPosts(@Param("posts") List<Post> posts);

}
