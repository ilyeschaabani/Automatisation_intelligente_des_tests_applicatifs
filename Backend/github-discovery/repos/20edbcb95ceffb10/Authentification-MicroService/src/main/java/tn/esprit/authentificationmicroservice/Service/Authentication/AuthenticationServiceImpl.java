package tn.esprit.authentificationmicroservice.Service.Authentication;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tn.esprit.authentificationmicroservice.Entity.Enum.Role;
import tn.esprit.authentificationmicroservice.Entity.User;
import tn.esprit.authentificationmicroservice.Repository.UserRepository;
import tn.esprit.authentificationmicroservice.Service.JWT.JWTService;
import tn.esprit.authentificationmicroservice.dto.JwtAuthenticationResponse;
import tn.esprit.authentificationmicroservice.dto.RefreshTokenrequest;
import tn.esprit.authentificationmicroservice.dto.SignInRequest;
import tn.esprit.authentificationmicroservice.dto.SignUpRequest;

import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JWTService jwtService;


    public User singUp(SignUpRequest signUpRequest) {
        User user = new User();
        user.setEmail(signUpRequest.getEmail());
        user.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));
        user.setRole(signUpRequest.getRole());
        user.setNom(signUpRequest.getNom());
        user.setPrenom(signUpRequest.getPrenom());
        user.setTelephone(signUpRequest.getTelephone());
        user.setAdresse(signUpRequest.getAdresse());
        return userRepository.save(user);
    }

    public JwtAuthenticationResponse login(SignInRequest signInRequest) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(signInRequest.getEmail(), signInRequest.getPassword()));
        var  user = userRepository.findByEmail(signInRequest.getEmail()).orElseThrow(() -> new RuntimeException("User not found"));
        var token = jwtService.generateToken(user);
        var refreshToken = jwtService.generateRefreshToken(new HashMap<>(),user);
        JwtAuthenticationResponse jwtAuthenticationResponse = new JwtAuthenticationResponse();
        jwtAuthenticationResponse.setToken(token);
        jwtAuthenticationResponse.setRefreshToken(refreshToken);
        return jwtAuthenticationResponse;
    }
    public JwtAuthenticationResponse refreshToken(RefreshTokenrequest refreshTokenrequest) {
        String userEmail = jwtService.extractUsername(refreshTokenrequest.getToken());
        User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new RuntimeException("User not found"));
        if (jwtService.validateToken(refreshTokenrequest.getToken(),user)) {
            var token = jwtService.generateToken(user);
            JwtAuthenticationResponse jwtAuthenticationResponse = new JwtAuthenticationResponse();
            jwtAuthenticationResponse.setToken(token);
            jwtAuthenticationResponse.setRefreshToken(refreshTokenrequest.getToken());
            return jwtAuthenticationResponse;
        }
        return null;
    }

}
