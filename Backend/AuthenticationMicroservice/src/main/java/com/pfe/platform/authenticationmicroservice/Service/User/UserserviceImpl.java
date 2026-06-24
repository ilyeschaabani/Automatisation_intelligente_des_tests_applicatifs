package com.pfe.platform.authenticationmicroservice.Service.User;

import com.pfe.platform.authenticationmicroservice.Entity.GlobalRole;
import com.pfe.platform.authenticationmicroservice.Entity.User;
import com.pfe.platform.authenticationmicroservice.Repository.UserRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserserviceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public UserDetailsService userDetailsService() {
        return email ->  userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username: " + email));

    }

    @Override
    public User getUserByEmail(String email) {
        if (email == null) throw new IllegalArgumentException("Email is required");
        String normalized = email.trim().toLowerCase();
        return userRepository.findByEmail(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username: " + normalized));
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public User getUserById(Long id) {
        if (id == null) throw new IllegalArgumentException("Id is required");
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with id: " + id));
    }

    @Override
    public User updateGlobalRoles(Long id, Set<GlobalRole> roles) {
        User user = getUserById(id);
        user.setGlobalRoles(roles == null ? new HashSet<>() : new HashSet<>(roles));
        return userRepository.save(user);
    }
}
