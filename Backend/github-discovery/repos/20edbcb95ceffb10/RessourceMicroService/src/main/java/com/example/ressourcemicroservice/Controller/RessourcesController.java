package com.example.ressourcemicroservice.Controller;

import com.example.ressourcemicroservice.Entity.Ressources;
import com.example.ressourcemicroservice.Service.RessourcesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/ressources")
public class RessourcesController {

    private final RessourcesService ressourcesService;

    public RessourcesController(RessourcesService ressourcesService) {
        this.ressourcesService = ressourcesService;
    }

    @PostMapping
    public ResponseEntity<Ressources> createRessource(@RequestBody Ressources ressource) {
        return ResponseEntity.ok(ressourcesService.createRessource(ressource));
    }

    @GetMapping
    public ResponseEntity<List<Ressources>> getAllRessources() {
        return ResponseEntity.ok(ressourcesService.getAllRessources());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Ressources> getRessourceById(@PathVariable Long id) {
        return ResponseEntity.ok(ressourcesService.getRessourceById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Ressources> updateRessource(
            @PathVariable Long id,
            @RequestBody Ressources updatedRessource) {
        return ResponseEntity.ok(ressourcesService.updateRessource(id, updatedRessource));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRessource(@PathVariable Long id) {
        ressourcesService.deleteRessource(id);
        return ResponseEntity.noContent().build();
    }
}