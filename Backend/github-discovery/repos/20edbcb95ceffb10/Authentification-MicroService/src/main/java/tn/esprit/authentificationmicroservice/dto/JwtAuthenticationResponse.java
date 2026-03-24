package tn.esprit.authentificationmicroservice.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JwtAuthenticationResponse {
    String token ;
    String refreshToken ;

}
