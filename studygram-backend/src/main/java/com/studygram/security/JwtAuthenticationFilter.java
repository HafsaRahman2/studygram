package com.studygram.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/*
 * JwtAuthenticationFilter - Reads the token on every request
 *
 * A filter runs before the request reaches any controller. This one has a
 * single job:
 *
 *   1. Look for an "Authorization: Bearer <token>" header
 *   2. Verify the token
 *   3. If it is valid, record who the caller is for the rest of the request
 *
 * It deliberately does NOT reject anything. If there is no token, or the token
 * is bad, it simply leaves the request unauthenticated and passes it along.
 * Deciding whether a particular URL requires authentication is SecurityConfig's
 * job, and keeping those two concerns apart means the rules live in one place
 * instead of being scattered through filter logic.
 *
 * OncePerRequestFilter guarantees this runs exactly once per request. A plain
 * filter can be invoked several times for a single request when the servlet
 * container forwards internally - which would mean verifying the same token
 * repeatedly for no reason.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    /*
     * Constructor injection rather than @Autowired on a field.
     *
     * It makes the dependency impossible to forget - the object cannot be built
     * without one - and it means this filter can be constructed directly in a
     * test with a stub, without Spring involved at all.
     */
    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null) {
            AuthenticatedUser user = jwtService.verifyToken(token);

            if (user != null) {
                /*
                 * Hand the identity to Spring Security.
                 *
                 * The first argument becomes the "principal", which is what
                 * @AuthenticationPrincipal hands to controllers. The second is
                 * the credentials - null here, because the token has already
                 * been verified and there is no password to keep hold of.
                 *
                 * The third is the list of authorities (roles). This app has no
                 * roles - every logged-in user can do the same things, and what
                 * they may do it TO is decided by ownership checks in the
                 * services. An empty list still counts as authenticated.
                 */
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, List.of());

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // Always continue. Rejecting is SecurityConfig's decision, not ours.
        filterChain.doFilter(request, response);
    }

    /*
     * Pull the token out of "Authorization: Bearer eyJhbGci...".
     *
     * Returns null for a missing header, or one in some other scheme (Basic,
     * for instance), which the caller treats the same as no token at all.
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);

        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }

        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

}
