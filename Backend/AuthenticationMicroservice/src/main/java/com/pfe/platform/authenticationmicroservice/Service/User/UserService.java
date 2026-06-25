package com.pfe.platform.authenticationmicroservice.Service.User;

import com.pfe.platform.authenticationmicroservice.Dto.CreateUserRequest;
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

    User updateGlobalRoles(Long id, Set<GlobalRole> roles);

    User createUser(CreateUserRequest request);

    void deleteUser(Long id);

    User setEnabled(Long id, boolean enabled);

    void updateLastLogin(String email);

    User updateProfile(Long id, String nom, String prenom);
}
