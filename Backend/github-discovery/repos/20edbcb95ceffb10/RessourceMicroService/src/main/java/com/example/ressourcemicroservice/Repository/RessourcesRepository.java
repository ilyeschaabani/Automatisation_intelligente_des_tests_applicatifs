package com.example.ressourcemicroservice.Repository;

import com.example.ressourcemicroservice.Entity.Ressources;
import com.example.ressourcemicroservice.Entity.Enumeration.CategoryRessource;
import com.example.ressourcemicroservice.Entity.Enumeration.TypeRessource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RessourcesRepository extends JpaRepository<Ressources, Long> {

    List<Ressources> findByTitreContainingIgnoreCase(String titre);
    List<Ressources> findByType(TypeRessource type);
    List<Ressources> findByCategory(CategoryRessource category);
}