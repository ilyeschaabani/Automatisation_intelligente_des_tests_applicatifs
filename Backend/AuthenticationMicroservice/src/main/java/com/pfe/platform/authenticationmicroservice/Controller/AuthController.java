package com.pfe.platform.authenticationmicroservice.Controller;

import com.pfe.platform.authenticationmicroservice.Config.CookieUtil;
import com.pfe.platform.authenticationmicroservice.Dto.JwtAuthenticationResponse;
import com.pfe.platform.authenticationmicroservice.Dto.RefreshTokenrequest;
import com.pfe.platform.authenticationmicroservice.Dto.SignInRequest;
import com.pfe.platform.authenticationmicroservice.Dto.SignUpRequest;
import com.pfe.platform.authenticationmicroservice.Entity.User;
import com.pfe.platform.authenticationmicroservice.Repository.UserRepository;
import com.pfe.platform.authenticationmicroservice.Service.Authentication.AuthenticationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.cookies.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookies.same-site:Lax}")
    private String cookieSameSite;

    @Value("${app.upload.dir:uploads/avatars}")
    private String uploadDir;

    @PostMapping("/signin")
    public ResponseEntity<JwtAuthenticationResponse> signIn(@Valid @RequestBody SignInRequest signInRequest,
                                                            HttpServletResponse response){
        JwtAuthenticationResponse auth = authenticationService.login(signInRequest);

        CookieUtil.addHttpOnlyCookie(response, CookieUtil.ACCESS_TOKEN_COOKIE, auth.getToken(), 60 * 60 * 10,
                cookieSecure, cookieSameSite);
        CookieUtil.addHttpOnlyCookie(response, CookieUtil.REFRESH_TOKEN_COOKIE, auth.getRefreshToken(), 60 * 60 * 24 * 7,
                cookieSecure, cookieSameSite);

        return ResponseEntity.ok(auth);
    }

    @PostMapping("/refreshToken")
    public ResponseEntity<JwtAuthenticationResponse> refreshToken(@Valid @RequestBody RefreshTokenrequest refreshTokenrequest,
                                                                  HttpServletResponse response){
        JwtAuthenticationResponse auth = authenticationService.refreshToken(refreshTokenrequest);

        CookieUtil.addHttpOnlyCookie(response, CookieUtil.ACCESS_TOKEN_COOKIE, auth.getToken(), 60 * 60 * 10,
                cookieSecure, cookieSameSite);
        CookieUtil.addHttpOnlyCookie(response, CookieUtil.REFRESH_TOKEN_COOKIE, auth.getRefreshToken(), 60 * 60 * 24 * 7,
                cookieSecure, cookieSameSite);

        return ResponseEntity.ok(auth);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        CookieUtil.clearCookie(response, CookieUtil.ACCESS_TOKEN_COOKIE, cookieSecure, cookieSameSite);
        CookieUtil.clearCookie(response, CookieUtil.REFRESH_TOKEN_COOKIE, cookieSecure, cookieSameSite);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/profile")
    public ResponseEntity<User> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        String email = userDetails.getUsername();
        User user = authenticationService.getProfile(email);
        return ResponseEntity.ok(user);
    }

    @PatchMapping("/profile")
    public ResponseEntity<User> updateProfile(@AuthenticationPrincipal UserDetails userDetails,
                                               @RequestBody Map<String, String> body) {
        String email = userDetails.getUsername();
        User user = authenticationService.getProfile(email);

        String nom = body.get("nom");
        String prenom = body.get("prenom");
        if (nom != null) user.setNom(nom.trim());
        if (prenom != null) user.setPrenom(prenom.trim());

        User saved = userRepository.save(user);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/changePassword")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal UserDetails userDetails,
                                            @RequestBody Map<String, String> body) {
        String email = userDetails.getUsername();
        User user = authenticationService.getProfile(email);

        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");

        if (currentPassword == null || currentPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Le mot de passe actuel est requis."));
        }
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "Le nouveau mot de passe doit contenir au moins 6 caractères."));
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Le mot de passe actuel est incorrect."));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Mot de passe mis à jour avec succès."));
    }

    @PostMapping("/uploadPhoto")
    public ResponseEntity<?> uploadPhoto(@AuthenticationPrincipal UserDetails userDetails,
                                         @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Aucun fichier fourni."));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Le fichier doit être une image."));
        }

        String email = userDetails.getUsername();
        User user = authenticationService.getProfile(email);

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
        Files.createDirectories(uploadPath);

        String extension = "";
        String originalName = file.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf('.'));
        }
        String filename = "avatar_" + user.getId() + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        String imageUrl = "/api/auth/photos/" + filename;
        user.setImageUrl(imageUrl);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
    }

    @GetMapping("/photos/{filename}")
    public ResponseEntity<Resource> getPhoto(@PathVariable String filename) throws IOException {
        Path filePath = Paths.get(uploadDir).toAbsolutePath().resolve(filename);

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri());
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) contentType = "application/octet-stream";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
