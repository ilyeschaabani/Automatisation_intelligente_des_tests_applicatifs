package com.pfe.platform.authenticationmicroservice.Controller;

import com.pfe.platform.authenticationmicroservice.Entity.PasswordResetRequest;
import com.pfe.platform.authenticationmicroservice.Entity.User;
import com.pfe.platform.authenticationmicroservice.Repository.PasswordResetRequestRepository;
import com.pfe.platform.authenticationmicroservice.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final UserRepository userRepository;
    private final PasswordResetRequestRepository resetRepo;

    @PostMapping("/request")
    public ResponseEntity<?> requestReset(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "L'adresse e-mail est requise."));
        }

        email = email.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "Si cette adresse existe, votre demande a été transmise à l'administrateur."));
        }

        User user = userOpt.get();

        if (resetRepo.existsByUserIdAndStatus(user.getId(), PasswordResetRequest.ResetStatus.PENDING)) {
            return ResponseEntity.ok(Map.of("message", "Une demande est déjà en attente. L'administrateur la traitera sous peu."));
        }

        PasswordResetRequest request = new PasswordResetRequest();
        request.setUser(user);
        request.setStatus(PasswordResetRequest.ResetStatus.PENDING);
        resetRepo.save(request);

        return ResponseEntity.ok(Map.of("message", "Votre demande a été transmise à l'administrateur. Vous recevrez un e-mail une fois approuvée."));
    }
}
