package com.pfe.platform.authenticationmicroservice.Service.Authentication;

import com.pfe.platform.authenticationmicroservice.Dto.JwtAuthenticationResponse;
import com.pfe.platform.authenticationmicroservice.Dto.RefreshTokenrequest;
import com.pfe.platform.authenticationmicroservice.Dto.SignInRequest;
import com.pfe.platform.authenticationmicroservice.Dto.SignUpRequest;
import com.pfe.platform.authenticationmicroservice.Entity.User;
import com.pfe.platform.authenticationmicroservice.Repository.UserRepository;
import com.pfe.platform.authenticationmicroservice.Service.JWT.JWTservice;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JWTservice jwtservice;
    private final com.pfe.platform.authenticationmicroservice.Service.User.UserService userService;

    @Override
    public User singUp(SignUpRequest signUpRequest) {
        String email = signUpRequest.getEmail().trim().toLowerCase();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = new User();
        user.setNom(signUpRequest.getNom());
        user.setPrenom(signUpRequest.getPrenom());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));
        user.setImageUrl(signUpRequest.getImageUrl());

        return userRepository.save(user);
    }

    @Override
    public JwtAuthenticationResponse login(SignInRequest signInRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(signInRequest.getEmail(), signInRequest.getPassword())
        );

        User user = (User) authentication.getPrincipal();
        String token = jwtservice.generateToken(user);
        String refreshToken = jwtservice.generateRefreshToken(Map.of(), user);

        userService.updateLastLogin(user.getEmail());

        JwtAuthenticationResponse response = new JwtAuthenticationResponse();
        response.setToken(token);
        response.setRefreshToken(refreshToken);
        return response;
    }

    @Override
    public JwtAuthenticationResponse refreshToken(RefreshTokenrequest refreshTokenrequest) {
        String refreshToken = refreshTokenrequest.getToken();
        String username = jwtservice.extractUsername(refreshToken);
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token user"));

        if (!jwtservice.validateToken(refreshToken, user)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        String newToken = jwtservice.generateToken(user);
        String newRefresh = jwtservice.generateRefreshToken(Map.of(), user);

        JwtAuthenticationResponse response = new JwtAuthenticationResponse();
        response.setToken(newToken);
        response.setRefreshToken(newRefresh);
        return response;
    }

    @Override
    public User getProfileById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @Override
    public User getProfile(String email) {
        if (email == null) throw new IllegalArgumentException("Email is required");
        String normalized = email.trim().toLowerCase();
        return userRepository.findByEmail(normalized).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
