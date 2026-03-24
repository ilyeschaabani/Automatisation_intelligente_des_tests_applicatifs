package tn.esprit.authentificationmicroservice.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/Encadrant")
@RequiredArgsConstructor
public class EncadrantController {
    @GetMapping
    public ResponseEntity<String> getEncadrant(){
        return ResponseEntity.ok("Hello Encadrant");
    }
}
