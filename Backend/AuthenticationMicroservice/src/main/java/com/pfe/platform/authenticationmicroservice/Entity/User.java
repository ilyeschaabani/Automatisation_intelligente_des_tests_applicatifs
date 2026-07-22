package com.pfe.platform.authenticationmicroservice.Entity;


import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "users")
public class User  implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Size(max = 100)
    String nom;

    @Size(max = 100)
    String prenom;

    @Email
    @NotBlank
    @Column(unique = true, nullable = false, length = 190)
    String email;

    @JsonIgnore
    @NotBlank
    @Column(nullable = false)
    String password;

    String imageUrl;

    // -----------------------------
    // GitHub integration
    // -----------------------------

    /** GitHub numeric user id from https://api.github.com/user */
    @Column(unique = true, length = 50)
    String githubId;

    /** GitHub login/username */
    String githubUsername;

    /** GitHub avatar url */
    String githubAvatarUrl;

    /** True when a GitHub account is linked */
    @Column
    Boolean githubConnected;

    /** Access token encrypted at rest (AES-GCM payload, base64) */
    @JsonIgnore
    @Column(length = 4096)
    String githubAccessToken;

    /** Timestamp when token was stored (for lifecycle checks/auditing) */
    Instant githubTokenCreatedAt;

    // -----------------------------
    // Account status & timestamps
    // -----------------------------

    @Column(nullable = false)
    Boolean enabled = true;

    @Column(nullable = false)
    Boolean superAdmin = false;

    @Column(updatable = false)
    Instant createdAt;

    Instant lastLoginAt;

    // -----------------------------
    // Roles (badges, multi-roles)
    // -----------------------------

    /** Roles globaux de l'utilisateur sur la plateforme. Un user peut en cumuler plusieurs. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_global_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    Set<GlobalRole> globalRoles = new HashSet<>();

    @PrePersist
    void onCreate() {
        if (email != null) email = email.trim().toLowerCase();
        if (createdAt == null) createdAt = Instant.now();
        if (enabled == null) enabled = true;
        if (superAdmin == null) superAdmin = false;
    }

    @PreUpdate
    void onUpdate() {
        if (email != null) email = email.trim().toLowerCase();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (globalRoles == null || globalRoles.isEmpty()) {
            return List.of();
        }
        return globalRoles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toList());
    }

    @Override
    public String getUsername() {
        // Return the email as the username for Spring Security
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
