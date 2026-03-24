package tn.esprit.authentificationmicroservice.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/Etudiant")
@RequiredArgsConstructor
public class EtudiantController {
    @GetMapping
    public ResponseEntity<String> getEtudiant(){
        return ResponseEntity.ok("Hello Etudiant");
    }
}
