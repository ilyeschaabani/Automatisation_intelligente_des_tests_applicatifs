package tn.esprit.authentificationmicroservice.Service.User;

import org.springframework.security.core.userdetails.UserDetailsService;

public interface UserService  {
    UserDetailsService userDetailsService();
}
