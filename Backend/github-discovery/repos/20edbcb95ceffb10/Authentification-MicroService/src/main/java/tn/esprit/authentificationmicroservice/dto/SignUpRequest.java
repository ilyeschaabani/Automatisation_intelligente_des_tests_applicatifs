package tn.esprit.authentificationmicroservice.dto;

import jakarta.validation.constraints.Email;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import tn.esprit.authentificationmicroservice.Entity.Enum.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SignUpRequest {
    @NotBlank
    String nom;

    @NotBlank
    String prenom;

    @Email
    @NotBlank
    String email;

    @NotBlank
    String password;

    @NotNull
    Role role;

    @NotBlank
    String telephone;

    @NotBlank
    String adresse;
}