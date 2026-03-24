package com.example.microserviceevaluation;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    @Autowired
    private EvaluationService evaluationService;


    // Create a new evaluation
    @PostMapping("/add")
    public Evaluation createEvaluation(@RequestBody Evaluation evaluation) {
        return evaluationService.createEvaluation(evaluation);

    }

    // Get all evaluations
    @GetMapping("/listeval")
    public ResponseEntity<List<Evaluation>> getAllEvaluations() {
        List<Evaluation> evaluations = evaluationService.getAllEvaluations();
        return ResponseEntity.ok(evaluations);
    }

    // Update an evaluation
    @PutMapping("/{idEvaluation}")
    public ResponseEntity<Evaluation> updateEvaluation(
            @PathVariable("idEvaluation") Long idEvaluation,
            @RequestBody Evaluation evaluation) {
        evaluation.setIdEvaluation(idEvaluation);
        Evaluation updatedEvaluation = evaluationService.updateEvaluation(evaluation);
        return ResponseEntity.ok(updatedEvaluation);
    }
//
    // Delete an evaluation
    @DeleteMapping("/{idEvaluation}")
    public void deleteEvaluation(@PathVariable Long idEvaluation) {
        evaluationService.deleteEvaluation(idEvaluation);

    }
//
    // Get evaluation by ID
@GetMapping("/{idEvaluation}")
public Evaluation getEvaluationById(@PathVariable Long idEvaluation) {
    return evaluationService.getEvaluationById(idEvaluation);
}

//
//    // Submit evaluation answers
//    @PostMapping("/{idEvaluation}/submit")
//    public ResponseEntity<?> submitEvaluationAnswers(
//            @PathVariable Long idEvaluation,
//            @RequestBody Map<String, Object> payload) {
//
//        List<Object> answers = (List<Object>) payload.get("answers");
//        // Process answers (you would typically save these to MySQL)
//        System.out.println("Réponses reçues pour l'évaluation " + idEvaluation + ": " + answers);
//
//        return ResponseEntity.ok("Réponses enregistrées avec succès !");
//    }
//
    @PostMapping("/{idEvaluation}/questions")
    public ResponseEntity<Evaluation> addQuestionsToEvaluation(@PathVariable Long idEvaluation, @RequestBody Set<Long> quesIds)
    {

        Evaluation evaluation = evaluationService.addQuestionsToEvaluation(idEvaluation, quesIds);
        return ResponseEntity.ok(evaluation);
    }
}
