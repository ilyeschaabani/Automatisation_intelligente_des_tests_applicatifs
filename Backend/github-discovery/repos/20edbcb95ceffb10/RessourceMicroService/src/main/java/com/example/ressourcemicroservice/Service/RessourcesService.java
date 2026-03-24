package com.example.ressourcemicroservice.Service;

import com.example.ressourcemicroservice.Entity.Ressources;
import com.example.ressourcemicroservice.Repository.RessourcesRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class RessourcesService {

    private final RessourcesRepository ressourcesRepository;

    public RessourcesService(RessourcesRepository ressourcesRepository) {
        this.ressourcesRepository = ressourcesRepository;
    }

    public Ressources createRessource(Ressources ressource) {
        return ressourcesRepository.save(ressource);
    }

    public List<Ressources> getAllRessources() {
        return ressourcesRepository.findAll();
    }

    public Ressources getRessourceById(Long id) {
        return ressourcesRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Ressource non trouvée avec l'ID : " + id));
    }

    public Ressources updateRessource(Long id, Ressources updatedRessource) {
        return ressourcesRepository.findById(id).map(ressource -> {
            ressource.setTitre(updatedRessource.getTitre());
            ressource.setDescription(updatedRessource.getDescription());
            ressource.setType(updatedRessource.getType());
            ressource.setDate(updatedRessource.getDate());
            ressource.setCategory(updatedRessource.getCategory());
            return ressourcesRepository.save(ressource);
        }).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Ressource non trouvée avec l'ID : " + id));
    }

    public void deleteRessource(Long id) {
        if (!ressourcesRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Ressource non trouvée avec l'ID : " + id);
        }
        ressourcesRepository.deleteById(id);
    }
}