package com.pfe.platform.authenticationmicroservice.Service.User;

import com.pfe.platform.authenticationmicroservice.Entity.GlobalRole;
import com.pfe.platform.authenticationmicroservice.Entity.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;
import java.util.Set;

public interface UserService {
    UserDetailsService userDetailsService();

    User getUserByEmail(String email);

    List<User> getAllUsers();

    User getUserById(Long id);

    /** Remplace l'ensemble des roles globaux d'un utilisateur (action reservee a un ADMIN). */
    User updateGlobalRoles(Long id, Set<GlobalRole> roles);
}
