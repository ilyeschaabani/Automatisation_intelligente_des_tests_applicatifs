package com.pfe.platform.authenticationmicroservice.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryResponse {
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String imageUrl;
    private String githubUsername;
}
