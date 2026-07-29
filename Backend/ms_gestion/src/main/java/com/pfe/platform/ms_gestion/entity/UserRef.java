package com.pfe.platform.ms_gestion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "users")
@Immutable
@Getter
public class UserRef {
    // The `users` table is owned by the Auth microservice, where User.id is
    // @GeneratedValue(IDENTITY). This mirror is read-only (@Immutable), but on a
    // fresh DB whichever service starts first creates the table. Declaring the same
    // identity strategy here guarantees the `id` column is created as an identity
    // column even when ms_gestion wins the race, so Auth's inserts don't hit a
    // NOT-NULL violation on `id`.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String imageUrl;
}
