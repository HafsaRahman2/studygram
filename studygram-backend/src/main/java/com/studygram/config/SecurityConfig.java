package com.studygram.config;

import com.studygram.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * SecurityConfig - Which URLs are public, and which require a token
 *
 * This class is the reason Spring Security's auto-configuration is no longer
 * switched off. The old application.properties contained:
 *
 *     spring.autoconfigure.exclude=...SecurityAutoConfiguration
 *
 * with the comment "we'll enable it later". Later is now. The exclusion existed
 * because the default auto-config puts a login form in front of everything,
 * which was not wanted - but the fix for that is to configure security, not to
 * remove it.
 *
 * THE RULE THIS FILE ENCODES
 *
 * Everything requires a valid token, EXCEPT the handful of endpoints you must
 * be able to reach before you have one: signing up, logging in, and resetting a
 * forgotten password.
 *
 * Note the direction. The list below is of PUBLIC endpoints, and anything not
 * on it is protected by default. Written the other way round - listing what to
 * protect - every new endpoint added later would be public until someone
 * remembered to add it. Defaulting to closed means forgetting is safe.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /*
     * The development fallback secret, used when nothing is configured.
     * Checked below so this can never silently be what protects real accounts.
     */
    static final String DEV_SECRET_MARKER = "dev-only-insecure-secret";

    @Value("${studygram.jwt.secret}")
    private String jwtSecret;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /*
     * Endpoints reachable without a token.
     *
     * Kept as a named constant so the test suite can assert on exactly this
     * list, rather than restating it and drifting out of step.
     */
    public static final String[] PUBLIC_ENDPOINTS = {
            "/api/hello",
            "/api/signup",
            "/api/login",
            "/api/forgot-password",
            "/api/reset-password",
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        warnIfUsingDevelopmentSecret();

        http
            /*
             * Apply the CORS rules from CorsConfig INSIDE the security chain,
             * so the browser's preflight OPTIONS request is answered before
             * authentication is considered.
             */
            .cors(cors -> {})

            /*
             * CSRF protection is turned off, and for once that is the correct
             * choice rather than a shortcut.
             *
             * Cross-Site Request Forgery attacks work by making a browser send
             * a request that it AUTOMATICALLY attaches your credentials to -
             * which is what cookies do. This API does not use cookies. The
             * token travels in an Authorization header that JavaScript has to
             * add deliberately, and another site's JavaScript cannot read our
             * token to add it. No automatic credential, no CSRF.
             *
             * If this app ever moves its token into a cookie, CSRF protection
             * must come back on.
             */
            .csrf(csrf -> csrf.disable())

            /*
             * Do not create HTTP sessions. Each request is authenticated purely
             * by its token, so there is no server-side state to keep, and the
             * API behaves identically no matter which server handles a request.
             */
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                    /*
                     * Browsers send an OPTIONS "preflight" request before a
                     * cross-origin POST, to ask whether the real request is
                     * allowed. It carries no Authorization header by design, so
                     * it must be permitted or every write from the frontend
                     * fails before it is even attempted.
                     */
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                    .requestMatchers(PUBLIC_ENDPOINTS).permitAll()

                    /*
                     * The topic list, and ONLY the list.
                     *
                     * The signup form asks new users to choose interests, and it
                     * cannot show them the options if fetching them requires an
                     * account they have not created yet. This is 64 seeded topic
                     * names - no user data, nothing private.
                     *
                     * NOTE THE EXACT PATH. It is deliberately NOT
                     * "/api/communities/**", because that would also expose
                     * /api/communities/{name}/posts - real posts by real people,
                     * to anyone at all. Opening a path one segment too wide is
                     * exactly how this kind of fix goes wrong, so there is a test
                     * asserting that sub-path is still protected.
                     */
                    .requestMatchers(HttpMethod.GET, "/api/communities").permitAll()

                    // Everything else needs a valid token.
                    .anyRequest().authenticated()
            )

            /*
             * Return a plain 401 instead of redirecting to a login page.
             *
             * The default behaviour assumes a server-rendered site and sends a
             * 302 to /login. A JSON client receiving an HTML login page in
             * place of its data is a confusing failure; a 401 is a clear one
             * the frontend can act on by logging the user out.
             */
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((request, response, authException) -> {
                        response.setStatus(401);
                        response.setContentType("text/plain");
                        response.getWriter().write("Authentication required");
                    })
            )

            /*
             * Slot our filter in before the username/password filter, so the
             * token has been read and the user identified by the time the
             * authorization rules above are evaluated.
             */
            .addFilterBefore(jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /*
     * BCryptPasswordEncoder as a shared bean.
     *
     * UserService used to construct its own with `new`. Declaring it once here
     * means there is a single configured instance, and a test can swap in a
     * cheaper encoder if hashing ever slows the suite down.
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /*
     * Shout loudly if the app is running on the development secret.
     *
     * A default that silently works is exactly how an insecure default reaches
     * production. This cannot stop someone deploying it, but it makes sure
     * nobody can say they were not told.
     */
    private void warnIfUsingDevelopmentSecret() {
        if (jwtSecret != null && jwtSecret.startsWith(DEV_SECRET_MARKER)) {
            log.warn("""

                    ***************************************************************
                     WARNING: using the built-in development JWT secret.

                     Anyone who knows this value can forge a token for ANY account.
                     Fine on localhost. Never deploy with it.

                     Generate a real one:   openssl rand -base64 32
                     Then set:              STUDYGRAM_JWT_SECRET=<the output>
                    ***************************************************************
                    """);
        }
    }

}
