package com.studygram.controller;

import com.studygram.service.GitHubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/*
 * GitHubController - API endpoints for GitHub integration
 *
 * Endpoints:
 *   GET /api/github/{username}          → Get full GitHub data
 *   GET /api/github/{username}/profile  → Get profile only
 *   GET /api/github/{username}/repos    → Get repositories
 *   GET /api/github/{username}/languages → Get languages
 */
@RestController
@RequestMapping("/api/github")
public class GitHubController {

    @Autowired
    private GitHubService gitHubService;

    /*
     * GET FULL GITHUB DATA
     *
     * URL: GET /api/github/hafsarahman
     *
     * Returns profile + repos + languages
     */
    @GetMapping("/{username}")
    public ResponseEntity<?> getGitHubData(@PathVariable String username) {
        try {

            Map<String, Object> data = gitHubService.getFullGitHubData(username);
            return ResponseEntity.ok(data);

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * GET PROFILE ONLY
     *
     * URL: GET /api/github/hafsarahman/profile
     */
    @GetMapping("/{username}/profile")
    public ResponseEntity<?> getProfile(@PathVariable String username) {
        try {

            Map<String, Object> profile = gitHubService.getProfile(username);
            return ResponseEntity.ok(profile);

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * GET REPOSITORIES
     *
     * URL: GET /api/github/hafsarahman/repos
     */
    @GetMapping("/{username}/repos")
    public ResponseEntity<?> getRepositories(@PathVariable String username) {
        try {

            List<Map<String, Object>> repos = gitHubService.getRepositories(username);
            return ResponseEntity.ok(repos);

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /*
     * GET LANGUAGES
     *
     * URL: GET /api/github/hafsarahman/languages
     */
    @GetMapping("/{username}/languages")
    public ResponseEntity<?> getLanguages(@PathVariable String username) {
        try {

            List<String> languages = gitHubService.getLanguages(username);
            return ResponseEntity.ok(languages);

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

}
