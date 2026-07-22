package com.pfe.platform.authenticationmicroservice.Dto;

import lombok.Data;
import java.util.Set;

@Data
public class CreateUserRequest {
    private String nom;
    private String prenom;
    private String email;
    private Set<String> roles;
}
