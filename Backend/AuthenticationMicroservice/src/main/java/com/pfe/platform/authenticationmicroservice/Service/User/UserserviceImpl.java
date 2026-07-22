package com.pfe.platform.authenticationmicroservice.Service.User;

import com.pfe.platform.authenticationmicroservice.Dto.CreateUserRequest;
import com.pfe.platform.authenticationmicroservice.Entity.GlobalRole;
import com.pfe.platform.authenticationmicroservice.Entity.User;
import com.pfe.platform.authenticationmicroservice.Repository.PasswordResetRequestRepository;
import com.pfe.platform.authenticationmicroservice.Repository.UserRepository;
import com.pfe.platform.authenticationmicroservice.Service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserserviceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordResetRequestRepository passwordResetRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Override
    public UserDetailsService userDetailsService() {
        return email -> userRepository.findByEmail(email)
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

        if (Boolean.TRUE.equals(user.getSuperAdmin()) && (roles == null || !roles.contains(GlobalRole.ADMIN))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Impossible de retirer le rôle ADMIN du Super Admin");
        }

        user.setGlobalRoles(roles == null ? new HashSet<>() : new HashSet<>(roles));
        return userRepository.save(user);
    }

    @Override
    public User createUser(CreateUserRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }

        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        String generatedPassword = generateRandomPassword();

        User user = new User();
        user.setNom(request.getNom());
        user.setPrenom(request.getPrenom());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(generatedPassword));
        user.setEnabled(true);

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            Set<GlobalRole> roles = request.getRoles().stream()
                    .filter(r -> r != null && !r.isBlank())
                    .map(r -> GlobalRole.valueOf(r.trim().toUpperCase()))
                    .collect(Collectors.toSet());
            user.setGlobalRoles(roles);
        }

        User saved = userRepository.save(user);

        String userName = "";
        if (request.getPrenom() != null) userName += request.getPrenom();
        if (request.getNom() != null) userName += (userName.isEmpty() ? "" : " ") + request.getNom();
        if (userName.isEmpty()) userName = email;

        emailService.sendWelcomeEmail(email, userName, generatedPassword);

        return saved;
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789@#$!";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = getUserById(id);

        if (Boolean.TRUE.equals(user.getSuperAdmin())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Le Super Admin ne peut pas être supprimé");
        }

        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (user.getEmail().equalsIgnoreCase(currentEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Impossible de supprimer votre propre compte");
        }

        passwordResetRequestRepository.deleteAllByUserId(id);
        userRepository.deleteById(id);
    }

    @Override
    public User setEnabled(Long id, boolean enabled) {
        User user = getUserById(id);

        if (Boolean.TRUE.equals(user.getSuperAdmin()) && !enabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Le Super Admin ne peut pas être désactivé");
        }

        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (user.getEmail().equalsIgnoreCase(currentEmail) && !enabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Impossible de désactiver votre propre compte");
        }

        user.setEnabled(enabled);
        return userRepository.save(user);
    }

    @Override
    public void updateLastLogin(String email) {
        userRepository.findByEmail(email.trim().toLowerCase()).ifPresent(user -> {
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);
        });
    }

    @Override
    public User updateProfile(Long id, String nom, String prenom) {
        User user = getUserById(id);
        if (nom != null) user.setNom(nom);
        if (prenom != null) user.setPrenom(prenom);
        return userRepository.save(user);
    }
}
