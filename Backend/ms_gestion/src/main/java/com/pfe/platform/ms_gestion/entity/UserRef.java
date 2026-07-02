package com.pfe.platform.ms_gestion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "users")
@Immutable
@Getter
public class UserRef {
    @Id
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String imageUrl;
}
