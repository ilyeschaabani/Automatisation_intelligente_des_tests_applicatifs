package com.pfe.platform.ms_gestion.dto.request;

import lombok.Data;

@Data
public class AddMemberRequest {
    private Long userId;
    private String email;
}
