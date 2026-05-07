package com.pfe.platform.msexecution.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
        Object userIdClaim = claims.get("userId");
        if (userIdClaim == null) {
            throw new RuntimeException("Token invalide : userId absent");
        }
        return Long.parseLong(userIdClaim.toString());
    }
}
