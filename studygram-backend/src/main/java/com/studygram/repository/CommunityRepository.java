package com.studygram.repository;

import com.studygram.entity.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/*
 * CommunityRepository - Database operations for Community
 */
public interface CommunityRepository extends JpaRepository<Community, Long> {

    /*
     * Find community by name (lowercase)
     * Used to get community details
     */
    Optional<Community> findByName(String name);

    /*
     * Find communities matching a list of names
     * Used to get user's communities based on their interests
     *
     * Example:
     *   interests: ["math", "science"]
     *   Returns: Math Community, Science Community
     */
    List<Community> findByNameIn(List<String> names);

    /*
     * Check if community exists
     */
    boolean existsByName(String name);

}
