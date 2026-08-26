package com.studygram.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/*
 * CORS Configuration - Allows the frontend to talk to the backend
 *
 * THE PROBLEM
 *   - Frontend runs on http://localhost:5173
 *   - Backend runs on http://localhost:8080
 *   - Different port means different "origin", and browsers block requests
 *     between origins by default. That rule is the Same-Origin Policy, and it
 *     is what stops a malicious page reading your email from another tab.
 *
 * THE SOLUTION
 *   - CORS (Cross-Origin Resource Sharing) lets the server name the origins it
 *     is willing to be called from.
 *
 * WHY THIS EXPOSES A CorsConfigurationSource AND NOT A CorsFilter
 *
 * It used to return a CorsFilter bean, which worked fine while Spring Security
 * was switched off. Now that security is enabled, a standalone filter bean is
 * registered AFTER Spring Security's own filter chain - so the browser's
 * preflight OPTIONS request would hit security first and be rejected before any
 * CORS header was ever added.
 *
 * Publishing a CorsConfigurationSource instead lets Spring Security pick it up
 * (see SecurityConfig's .cors(...)) and apply it inside the chain, at the right
 * point, before authentication runs.
 */
@Configuration
public class CorsConfig {

    /*
     * Which origins may call this API, read from configuration rather than
     * hardcoded. Deploying to a real domain is then an environment change, not
     * a code change.
     */
    @Value("${studygram.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Includes Authorization, which is how the token reaches us.
        config.setAllowedHeaders(List.of("*"));

        /*
         * The token travels in a header that our JavaScript sets explicitly,
         * not in a cookie, so the browser does not need permission to send
         * credentials automatically. Leaving this off keeps the policy tighter,
         * and it is what allows an exact origin list to remain strict.
         */
        config.setAllowCredentials(false);

        /*
         * How long a browser may cache the preflight answer, in seconds.
         * Without it, every POST costs two round trips instead of one.
         */
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        return source;
    }

}
