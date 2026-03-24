package com.example.microserviceevaluation;


import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface IEvaluationService {
    //Créer une nouvelle évaluation
    Evaluation createEvaluation(Evaluation evaluation);

    //    // Lire toutes les évaluations
    List<Evaluation> getAllEvaluations();
    //
    // Lire une évaluation par ID
    Evaluation getEvaluationById(Long idEvaluation);

    // Mettre à jour une évaluation existante
    Evaluation updateEvaluation(Evaluation evaluation);

//
//    //
//    // Supprimer une évaluation par ID
    void deleteEvaluation(Long idEvaluation);

//
//    void submitEvaluationAnswers(Long evaluationId, Map<Long, Object> answers);
//
//
//    List<Question> getQuestionsByEvaluation(Long idEvaluation);
//
//
   public Evaluation addQuestionsToEvaluation(Long idEvaluation, Set<Long> quesIds);
}
