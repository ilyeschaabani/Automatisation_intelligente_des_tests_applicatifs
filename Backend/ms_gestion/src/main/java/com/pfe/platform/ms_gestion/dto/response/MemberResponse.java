package com.pfe.platform.ms_gestion.dto.response;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemberResponse {
    private Long userId;
    private String nom;
    private String prenom;
    private String email;
    private String imageUrl;
}
