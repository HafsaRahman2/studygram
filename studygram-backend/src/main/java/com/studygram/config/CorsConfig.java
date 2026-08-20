package com.studygram.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/*
 * CORS Configuration - Allows frontend to talk to backend
 *
 * PROBLEM:
 * - Frontend runs on http://localhost:5173
 * - Backend runs on http://localhost:8080
 * - Browsers block requests between different "origins" (domain + port)
 * - This is a security feature called "Same-Origin Policy"
 *
 * SOLUTION:
 * - Tell the backend: "It's okay, allow requests from localhost:5173"
 * - This is called CORS (Cross-Origin Resource Sharing)
 */
@Configuration
public class CorsConfig {

    /*
     * Read the allowed origins from application.properties instead of hardcoding
     * them. Spring splits a comma-separated property straight into a List, so
     * deploying to a real domain later means changing an environment variable
     * rather than editing and recompiling this class.
     */
    @Value("${studygram.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {

        // Create CORS configuration
        CorsConfiguration config = new CorsConfiguration();

        // Allow requests from our React frontend
        allowedOrigins.forEach(config::addAllowedOrigin);

        // Allow these HTTP methods
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");

        // Allow these headers (Content-Type is needed for JSON)
        config.addAllowedHeader("*");

        // Allow cookies/credentials if needed later
        config.setAllowCredentials(true);

        // Apply this config to all API endpoints
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        return new CorsFilter(source);
    }
}
