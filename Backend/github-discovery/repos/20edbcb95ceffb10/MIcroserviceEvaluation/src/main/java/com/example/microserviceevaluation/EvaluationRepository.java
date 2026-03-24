package com.example.microserviceevaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    Optional<Evaluation> findFirstByIdEvaluation(Long idEvaluation);

    Optional<Evaluation> findByIdEvaluation(Long idEvaluation);


    //  Optional<Evaluation> findByIdEvaluation(Long idEvaluation);
    // Utilisez le bon nom de propriété pour l'ID

}
