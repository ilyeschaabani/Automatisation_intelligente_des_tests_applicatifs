package tn.esprit.authentificationmicroservice.Entity;


import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Date;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)

public class ForgotPassword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer fpid;
    @Column(nullable = false)
    Integer otp;
    @Column(nullable = false)
    Date expirationTime;
    @OneToOne
    User user;

}
