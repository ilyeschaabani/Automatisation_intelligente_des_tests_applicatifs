package com.pfe.platform.authenticationmicroservice.Controller;

import com.pfe.platform.authenticationmicroservice.Dto.CreateUserRequest;
import com.pfe.platform.authenticationmicroservice.Dto.UpdateRolesRequest;
import com.pfe.platform.authenticationmicroservice.Dto.UserAdminResponse;
import com.pfe.platform.authenticationmicroservice.Entity.GlobalRole;
import com.pfe.platform.authenticationmicroservice.Entity.PasswordResetRequest;
import com.pfe.platform.authenticationmicroservice.Entity.User;
import com.pfe.platform.authenticationmicroservice.Repository.PasswordResetRequestRepository;
import com.pfe.platform.authenticationmicroservice.Service.EmailService;
import com.pfe.platform.authenticationmicroservice.Repository.UserRepository;
import com.pfe.platform.authenticationmicroservice.Service.User.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordResetRequestRepository resetRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @GetMapping
    public ResponseEntity<List<UserAdminResponse>> list() {
        List<UserAdminResponse> users = userService.getAllUsers()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserAdminResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(userService.getUserById(id)));
    }

    @PostMapping
    public ResponseEntity<UserAdminResponse> create(@RequestBody CreateUserRequest request) {
        User created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/roles")
    public ResponseEntity<UserAdminResponse> updateRoles(@PathVariable Long id,
                                                         @RequestBody UpdateRolesRequest request) {
        Set<GlobalRole> roles = parseRoles(request);
        User updated = userService.updateGlobalRoles(id, roles);
        return ResponseEntity.ok(toResponse(updated));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UserAdminResponse> setStatus(@PathVariable Long id,
                                                       @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        User updated = userService.setEnabled(id, enabled);
        return ResponseEntity.ok(toResponse(updated));
    }

    @PatchMapping("/{id}/profile")
    public ResponseEntity<UserAdminResponse> updateProfile(@PathVariable Long id,
                                                           @RequestBody Map<String, String> body) {
        User updated = userService.updateProfile(id, body.get("nom"), body.get("prenom"));
        return ResponseEntity.ok(toResponse(updated));
    }

    // ─── Password Reset Requests ────────────────────────────────

    @GetMapping("/password-reset-requests")
    public ResponseEntity<List<Map<String, Object>>> getResetRequests() {
        List<PasswordResetRequest> requests = resetRepo.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = requests.stream().map(r -> {
            User u = r.getUser();
            String displayName = "";
            if (u.getPrenom() != null) displayName += u.getPrenom();
            if (u.getNom() != null) displayName += (displayName.isEmpty() ? "" : " ") + u.getNom();
            if (displayName.isEmpty()) displayName = u.getEmail();

            return Map.<String, Object>of(
                    "id", r.getId(),
                    "userId", u.getId(),
                    "email", u.getEmail(),
                    "displayName", displayName,
                    "status", r.getStatus().name(),
                    "createdAt", r.getCreatedAt().toString()
            );
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/password-reset-requests/pending-count")
    public ResponseEntity<Map<String, Long>> getPendingCount() {
        long count = resetRepo.findByStatusOrderByCreatedAtDesc(PasswordResetRequest.ResetStatus.PENDING).size();
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/password-reset-requests/{id}/approve")
    public ResponseEntity<?> approveReset(@PathVariable Long id,
                                           @AuthenticationPrincipal UserDetails adminDetails) {
        PasswordResetRequest request = resetRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Demande introuvable"));

        if (request.getStatus() != PasswordResetRequest.ResetStatus.PENDING) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cette demande a déjà été traitée."));
        }

        String tempPassword = generateTempPassword();

        User user = request.getUser();
        user.setPassword(passwordEncoder.encode(tempPassword));
        userRepository.save(user);

        User admin = userService.getUserByEmail(adminDetails.getUsername());
        request.setStatus(PasswordResetRequest.ResetStatus.APPROVED);
        request.setProcessedAt(Instant.now());
        request.setProcessedBy(admin.getId());
        resetRepo.save(request);

        String userName = "";
        if (user.getPrenom() != null) userName += user.getPrenom();
        if (user.getNom() != null) userName += (userName.isEmpty() ? "" : " ") + user.getNom();
        if (userName.isEmpty()) userName = user.getEmail();

        emailService.sendPasswordResetEmail(user.getEmail(), userName, tempPassword);

        return ResponseEntity.ok(Map.of("message", "Mot de passe réinitialisé et e-mail envoyé."));
    }

    @PostMapping("/password-reset-requests/{id}/reject")
    public ResponseEntity<?> rejectReset(@PathVariable Long id,
                                          @AuthenticationPrincipal UserDetails adminDetails) {
        PasswordResetRequest request = resetRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Demande introuvable"));

        if (request.getStatus() != PasswordResetRequest.ResetStatus.PENDING) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cette demande a déjà été traitée."));
        }

        User admin = userService.getUserByEmail(adminDetails.getUsername());
        request.setStatus(PasswordResetRequest.ResetStatus.REJECTED);
        request.setProcessedAt(Instant.now());
        request.setProcessedBy(admin.getId());
        resetRepo.save(request);

        return ResponseEntity.ok(Map.of("message", "Demande rejetée."));
    }

    // ─── Email diagnostic (temporaire) ─────────────────────────

    @PostMapping("/test-email")
    public ResponseEntity<Map<String, Object>> testEmail(@RequestBody Map<String, String> body) {
        String to = body.get("to");
        if (to == null || to.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Champ 'to' requis"));
        }
        try {
            emailService.sendTestEmail(to.trim());
            return ResponseEntity.ok(Map.of("ok", true, "message", "E-mail de test envoyé à " + to));
        } catch (Exception e) {
            String root = e.getMessage();
            Throwable cause = e.getCause();
            while (cause != null) { root = cause.getMessage(); cause = cause.getCause(); }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false,
                            "error", e.getClass().getSimpleName(),
                            "message", String.valueOf(e.getMessage()),
                            "rootCause", String.valueOf(root)));
        }
    }

    // ─── Helpers ────────────────────────────────────────────────

    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private Set<GlobalRole> parseRoles(UpdateRolesRequest request) {
        if (request == null || request.getRoles() == null) {
            return Set.of();
        }
        try {
            return request.getRoles().stream()
                    .filter(r -> r != null && !r.isBlank())
                    .map(r -> GlobalRole.valueOf(r.trim().toUpperCase()))
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Role invalide. Valeurs autorisees : ADMIN, TEST_MANAGER, TESTEUR, OBSERVATEUR");
        }
    }

    private UserAdminResponse toResponse(User user) {
        return UserAdminResponse.builder()
                .id(user.getId())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .email(user.getEmail())
                .imageUrl(user.getImageUrl())
                .githubUsername(user.getGithubUsername())
                .githubConnected(user.getGithubConnected())
                .roles(user.getGlobalRoles())
                .enabled(user.getEnabled())
                .superAdmin(user.getSuperAdmin())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
