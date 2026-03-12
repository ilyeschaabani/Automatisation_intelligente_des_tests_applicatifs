package com.pfe.platform.authenticationmicroservice.Config;

import com.pfe.platform.authenticationmicroservice.Service.JWT.JWTservice;
import com.pfe.platform.authenticationmicroservice.Service.User.UserService;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JWtAuthFIlter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JWtAuthFIlter.class);

    private final JWTservice jwtUtils;
    private final UserService UserUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String jwt = null;

        final String authorizationHeader = request.getHeader("Authorization");
        if (!StringUtils.isEmpty(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
        }

        // If no Authorization header, try HttpOnly cookie
        if (StringUtils.isEmpty(jwt)) {
            jwt = CookieUtil.getCookieValue(request, CookieUtil.ACCESS_TOKEN_COOKIE);
        }

        if (StringUtils.isEmpty(jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String userEmail;
        try {
            userEmail = jwtUtils.extractUsername(jwt);
        } catch (Exception ex) {
            // malformed token
            logger.debug("Failed to extract username from JWT", ex);
            filterChain.doFilter(request, response);
            return;
        }

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = UserUtils.userDetailsService().loadUserByUsername(userEmail);

                if (jwtUtils.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
                } else {
                    logger.debug("Invalid JWT token for user: {}", userEmail);
                }
            } catch (UsernameNotFoundException ex) {
                // Token refers to a user that no longer exists -> treat as unauthenticated.
                logger.debug("JWT user no longer exists: {}", userEmail);
            } catch (Exception ex) {
                logger.debug("JWT authentication failed", ex);
            }
        }

        filterChain.doFilter(request, response);
    }
}
