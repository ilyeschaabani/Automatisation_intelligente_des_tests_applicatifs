package com.pfe.platform.authenticationmicroservice.Dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)

public class SignInRequest {
    String email;
    String password;

}
