package com.studygram.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;

/*
 * GitHubService - Fetches public GitHub data
 *
 * Uses GitHub's public REST API (no API key needed for public repos)
 * Docs: https://docs.github.com/en/rest
 *
 * Features:
 *   - Get user profile
 *   - Get repositories
 *   - Get languages used
 */
@Service
public class GitHubService {

    private static final String GITHUB_API = "https://api.github.com";

    private final RestTemplate restTemplate = new RestTemplate();

    /*
     * GET GITHUB PROFILE
     *
     * Returns basic info: name, bio, avatar, public repos count
     */
    public Map<String, Object> getProfile(String username) {

        String url = GITHUB_API + "/users/" + username;

        try {
            // GitHub API requires User-Agent header
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Studygram-App");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            Map body = response.getBody();

            // Return only the fields we need
            Map<String, Object> profile = new HashMap<>();
            profile.put("username", body.get("login"));
            profile.put("name", body.get("name"));
            profile.put("bio", body.get("bio"));
            profile.put("avatarUrl", body.get("avatar_url"));
            profile.put("profileUrl", body.get("html_url"));
            profile.put("publicRepos", body.get("public_repos"));
            profile.put("followers", body.get("followers"));
            profile.put("following", body.get("following"));

            return profile;

        } catch (Exception e) {
            throw new RuntimeException("GitHub user not found: " + username);
        }
    }

    /*
     * GET REPOSITORIES
     *
     * Returns list of public repos with name, description, language, stars
     */
    public List<Map<String, Object>> getRepositories(String username) {

        String url = GITHUB_API + "/users/" + username + "/repos?sort=updated&per_page=10";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Studygram-App");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    List.class
            );

            List<Map> repos = response.getBody();
            List<Map<String, Object>> result = new ArrayList<>();

            for (Map repo : repos) {
                Map<String, Object> repoInfo = new HashMap<>();
                repoInfo.put("name", repo.get("name"));
                repoInfo.put("description", repo.get("description"));
                repoInfo.put("language", repo.get("language"));
                repoInfo.put("stars", repo.get("stargazers_count"));
                repoInfo.put("forks", repo.get("forks_count"));
                repoInfo.put("url", repo.get("html_url"));

                result.add(repoInfo);
            }

            return result;

        } catch (Exception e) {
            throw new RuntimeException("Could not fetch repositories for: " + username);
        }
    }

    /*
     * GET LANGUAGES
     *
     * Returns list of unique languages used across all repos
     */
    public List<String> getLanguages(String username) {

        List<Map<String, Object>> repos = getRepositories(username);

        // Use Set to get unique languages
        Set<String> languages = new HashSet<>();

        for (Map<String, Object> repo : repos) {
            String language = (String) repo.get("language");
            if (language != null) {
                languages.add(language);
            }
        }

        return new ArrayList<>(languages);
    }

    /*
     * GET FULL GITHUB DATA
     *
     * Returns profile + repos + languages in one call
     */
    public Map<String, Object> getFullGitHubData(String username) {

        Map<String, Object> data = new HashMap<>();

        data.put("profile", getProfile(username));
        data.put("repositories", getRepositories(username));
        data.put("languages", getLanguages(username));

        return data;
    }

}
