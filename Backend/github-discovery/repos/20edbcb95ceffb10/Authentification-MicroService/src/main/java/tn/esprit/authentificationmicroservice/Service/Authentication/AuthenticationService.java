package tn.esprit.authentificationmicroservice.Service.Authentication;

import tn.esprit.authentificationmicroservice.Entity.User;
import tn.esprit.authentificationmicroservice.dto.JwtAuthenticationResponse;
import tn.esprit.authentificationmicroservice.dto.RefreshTokenrequest;
import tn.esprit.authentificationmicroservice.dto.SignInRequest;
import tn.esprit.authentificationmicroservice.dto.SignUpRequest;

public interface AuthenticationService {
    User singUp(SignUpRequest signUpRequest);
    JwtAuthenticationResponse login(SignInRequest signInRequest);
    JwtAuthenticationResponse refreshToken(RefreshTokenrequest refreshTokenrequest);
}
