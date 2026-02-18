package com.pfe.platform.authenticationmicroservice.Controller;

import com.pfe.platform.authenticationmicroservice.Config.CookieUtil;
import com.pfe.platform.authenticationmicroservice.Dto.JwtAuthenticationResponse;
import com.pfe.platform.authenticationmicroservice.Dto.RefreshTokenrequest;
import com.pfe.platform.authenticationmicroservice.Dto.SignInRequest;
import com.pfe.platform.authenticationmicroservice.Dto.SignUpRequest;
import com.pfe.platform.authenticationmicroservice.Entity.User;
import com.pfe.platform.authenticationmicroservice.Service.Authentication.AuthenticationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    @Value("${app.cookies.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookies.same-site:Lax}")
    private String cookieSameSite;

    @PostMapping("/signup")
    public ResponseEntity<User> signUp(@Valid @RequestBody SignUpRequest userCreateDTO){
        User user = authenticationService.singUp(userCreateDTO);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/signin")
    public ResponseEntity<JwtAuthenticationResponse> signIn(@Valid @RequestBody SignInRequest signInRequest,
                                                            HttpServletResponse response){
        JwtAuthenticationResponse auth = authenticationService.login(signInRequest);

        // access token: 10h
        CookieUtil.addHttpOnlyCookie(response, CookieUtil.ACCESS_TOKEN_COOKIE, auth.getToken(), 60 * 60 * 10,
                cookieSecure, cookieSameSite);
        // refresh token: 7d
        CookieUtil.addHttpOnlyCookie(response, CookieUtil.REFRESH_TOKEN_COOKIE, auth.getRefreshToken(), 60 * 60 * 24 * 7,
                cookieSecure, cookieSameSite);

        return ResponseEntity.ok(auth);
    }

    @PostMapping("/refreshToken")
    public ResponseEntity<JwtAuthenticationResponse> refreshToken(@Valid @RequestBody RefreshTokenrequest refreshTokenrequest,
                                                                  HttpServletResponse response){
        JwtAuthenticationResponse auth = authenticationService.refreshToken(refreshTokenrequest);

        CookieUtil.addHttpOnlyCookie(response, CookieUtil.ACCESS_TOKEN_COOKIE, auth.getToken(), 60 * 60 * 10,
                cookieSecure, cookieSameSite);
        CookieUtil.addHttpOnlyCookie(response, CookieUtil.REFRESH_TOKEN_COOKIE, auth.getRefreshToken(), 60 * 60 * 24 * 7,
                cookieSecure, cookieSameSite);

        return ResponseEntity.ok(auth);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        CookieUtil.clearCookie(response, CookieUtil.ACCESS_TOKEN_COOKIE, cookieSecure, cookieSameSite);
        CookieUtil.clearCookie(response, CookieUtil.REFRESH_TOKEN_COOKIE, cookieSecure, cookieSameSite);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/profile")
    public ResponseEntity<User> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        String email = userDetails.getUsername();
        User user = authenticationService.getProfile(email);
        return ResponseEntity.ok(user);
    }
}
