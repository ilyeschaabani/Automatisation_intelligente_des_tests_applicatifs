package com.pfe.platform.authenticationmicroservice.Service.Authentication;

import com.pfe.platform.authenticationmicroservice.Dto.JwtAuthenticationResponse;
import com.pfe.platform.authenticationmicroservice.Dto.RefreshTokenrequest;
import com.pfe.platform.authenticationmicroservice.Dto.SignInRequest;
import com.pfe.platform.authenticationmicroservice.Dto.SignUpRequest;
import com.pfe.platform.authenticationmicroservice.Entity.User;

public interface AuthenticationService {
    User singUp(SignUpRequest signUpRequest);
    JwtAuthenticationResponse login(SignInRequest signInRequest);
    JwtAuthenticationResponse refreshToken(RefreshTokenrequest refreshTokenrequest);
    public User getProfileById(Long id);
    public User getProfile(String email);
}
