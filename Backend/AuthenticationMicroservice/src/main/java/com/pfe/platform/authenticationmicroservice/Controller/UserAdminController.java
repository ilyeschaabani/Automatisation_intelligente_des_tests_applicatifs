package com.pfe.platform.authenticationmicroservice.Controller;

import com.pfe.platform.authenticationmicroservice.Dto.CreateUserRequest;
import com.pfe.platform.authenticationmicroservice.Dto.UpdateRolesRequest;
import com.pfe.platform.authenticationmicroservice.Dto.UserAdminResponse;
import com.pfe.platform.authenticationmicroservice.Entity.GlobalRole;
import com.pfe.platform.authenticationmicroservice.Entity.User;
import com.pfe.platform.authenticationmicroservice.Service.User.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
                    "Role invalide. Valeurs autorisees : ADMIN, TEST_MANAGER, QA_ENGINEER, DEVELOPER, VIEWER");
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
