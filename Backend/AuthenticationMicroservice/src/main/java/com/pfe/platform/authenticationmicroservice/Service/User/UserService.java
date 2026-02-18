package com.pfe.platform.authenticationmicroservice.Service.User;

import org.springframework.security.core.userdetails.UserDetailsService;

public interface UserService {
    UserDetailsService userDetailsService();
}
