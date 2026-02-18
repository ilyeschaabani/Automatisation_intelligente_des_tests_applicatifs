package com.pfe.platform.authenticationmicroservice.Dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.Set;
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)

public class SignUpRequest {
     String nom;
     String prenom;
     String email;
     String password;
     String imageUrl;
//     Set<Long> roleIds; // Change from role to roleIds
}
