package com.pfe.platform.authenticationmicroservice.Config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class CookieUtil {

    private CookieUtil() {
    }

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    public static void addHttpOnlyCookie(HttpServletResponse response,
                                         String name,
                                         String value,
                                         int maxAgeSeconds,
                                         boolean secure,
                                         String sameSite) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        response.addCookie(cookie);

        // Add SameSite attribute (Servlet Cookie API doesn't support it directly)
        String headerValue = String.format("%s=%s; Max-Age=%d; Path=/; HttpOnly%s; SameSite=%s",
                name,
                value,
                maxAgeSeconds,
                secure ? "; Secure" : "",
                sameSite);
        response.addHeader("Set-Cookie", headerValue);
    }

    public static void clearCookie(HttpServletResponse response, String name, boolean secure, String sameSite) {
        addHttpOnlyCookie(response, name, "", 0, secure, sameSite);
    }

    public static String getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}

