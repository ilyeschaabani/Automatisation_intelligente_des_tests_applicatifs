package com.pfe.platform.authenticationmicroservice.Controller;

import com.pfe.platform.authenticationmicroservice.Dto.UpdateRolesRequest;
import com.pfe.platform.authenticationmicroservice.Dto.UserAdminResponse;
import com.pfe.platform.authenticationmicroservice.Entity.GlobalRole;
import com.pfe.platform.authenticationmicroservice.Entity.User;
import com.pfe.platform.authenticationmicroservice.Service.User.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Administration des roles globaux. Toutes les routes sont reservees aux utilisateurs
 * portant le role ADMIN (controle par @PreAuthorize + matcher SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserService userService;

    /** Liste tous les utilisateurs avec leurs roles, pour l'ecran de gestion. */
    @GetMapping
    public ResponseEntity<List<UserAdminResponse>> list() {
        List<UserAdminResponse> users = userService.getAllUsers()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    /** Detail d'un utilisateur. */
    @GetMapping("/{id}")
    public ResponseEntity<UserAdminResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(userService.getUserById(id)));
    }

    /** Remplace l'ensemble des roles d'un utilisateur. Body: { "roles": ["ADMIN","TESTER"] }. */
    @PatchMapping("/{id}/roles")
    public ResponseEntity<UserAdminResponse> updateRoles(@PathVariable Long id,
                                                         @RequestBody UpdateRolesRequest request) {
        Set<GlobalRole> roles = parseRoles(request);
        User updated = userService.updateGlobalRoles(id, roles);
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
                    "Role invalide. Valeurs autorisees : ADMIN, TESTER, DEVELOPER");
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
                .build();
    }
}
