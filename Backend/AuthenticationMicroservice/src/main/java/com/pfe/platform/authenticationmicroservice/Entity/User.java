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
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

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
    @Column(length = 4096)
    String githubAccessToken;

    /** Timestamp when token was stored (for lifecycle checks/auditing) */
    Instant githubTokenCreatedAt;

    @PrePersist
    @PreUpdate
    void normalize() {
        if (email != null) {
            email = email.trim().toLowerCase();
        }
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
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
        return UserDetails.super.isEnabled();
    }
}
